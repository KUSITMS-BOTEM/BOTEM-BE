package com.botem.domain.funding.service

import com.botem.domain.account.service.AccountNumberCipher
import com.botem.domain.funding.dto.request.CreateContributionRequest
import com.botem.domain.funding.dto.request.CreateMessageContributionRequest
import com.botem.domain.funding.dto.request.MarkTransferSentRequest
import com.botem.domain.funding.dto.response.ContributionPayoutAccountResponse
import com.botem.domain.funding.dto.response.ContributionResponse
import com.botem.domain.funding.dto.response.MessageContributionResponse
import com.botem.domain.funding.dto.response.TransferSentResponse
import com.botem.domain.funding.entity.Contribution
import com.botem.domain.funding.entity.ContributionStatus
import com.botem.domain.funding.entity.Funding
import com.botem.domain.funding.entity.FundingStatus
import com.botem.domain.funding.entity.RollingPaperMessage
import com.botem.domain.funding.exception.FundingErrorCode
import com.botem.domain.funding.repository.ContributionRepository
import com.botem.domain.funding.repository.FundingRepository
import com.botem.domain.funding.repository.RollingPaperMessageRepository
import com.botem.domain.user.exception.UserErrorCode
import com.botem.domain.user.repository.UserRepository
import com.botem.global.exception.BusinessException
import com.botem.global.exception.CommonErrorCode
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

@Service
class ContributionService(
    private val fundingRepository: FundingRepository,
    private val contributionRepository: ContributionRepository,
    private val rollingPaperMessageRepository: RollingPaperMessageRepository,
    private val userRepository: UserRepository,
    private val accountNumberCipher: AccountNumberCipher,
    private val clock: Clock,
) {

    private val passwordEncoder = BCryptPasswordEncoder()

    @Transactional
    fun contribute(userId: Long?, fundingId: Long, request: CreateContributionRequest): ContributionResponse {
        val input = prepareParticipantInput(
            userId = userId,
            participantName = request.participantName,
            message = request.message,
            paperTemplateCode = request.paperTemplateCode,
            guestPassword = request.guestPassword,
        )
        val funding = lockFunding(fundingId)

        contributionRepository.findByFundingIdAndRequestKey(fundingId, request.requestKey)?.let { existing ->
            if (existing.participantUserId != userId || existing.amount != request.amount) {
                throw BusinessException(FundingErrorCode.CONTRIBUTION_REQUEST_KEY_CONFLICT)
            }
            return ContributionResponse.of(existing, payoutAccountOf(funding))
        }

        validateContributable(funding, request.amount)
        validateGuestIdentityAvailable(fundingId, input)
        val contribution = saveWithMessage(
            userId = userId,
            fundingId = fundingId,
            requestKey = request.requestKey,
            amount = request.amount,
            status = ContributionStatus.AWAITING_TRANSFER,
            input = input,
        )

        return ContributionResponse.of(contribution, payoutAccountOf(funding))
    }

    // 금액 없이 메시지만 남기는 참여. 달성률에는 반영하지 않고 목표 달성 후에도 마감 전까지 받는다
    @Transactional
    fun leaveMessage(
        userId: Long?,
        fundingId: Long,
        request: CreateMessageContributionRequest,
    ): MessageContributionResponse {
        val input = prepareParticipantInput(
            userId = userId,
            participantName = request.participantName,
            message = request.message,
            paperTemplateCode = request.paperTemplateCode,
            guestPassword = request.guestPassword,
        )
        val funding = lockFunding(fundingId)

        contributionRepository.findByFundingIdAndRequestKey(fundingId, request.requestKey)?.let { existing ->
            if (existing.participantUserId != userId || existing.status != ContributionStatus.MESSAGE_ONLY) {
                throw BusinessException(FundingErrorCode.CONTRIBUTION_REQUEST_KEY_CONFLICT)
            }
            return MessageContributionResponse.from(existing)
        }

        validateOpen(funding)
        validateGuestIdentityAvailable(fundingId, input)
        val contribution = saveWithMessage(
            userId = userId,
            fundingId = fundingId,
            requestKey = request.requestKey,
            amount = 0L,
            status = ContributionStatus.MESSAGE_ONLY,
            input = input,
        )

        return MessageContributionResponse.from(contribution)
    }

    // 비밀번호 실패 횟수는 예외를 던져도 남아야 하므로 BusinessException에는 롤백하지 않는다
    @Transactional(noRollbackFor = [BusinessException::class])
    fun markTransferSent(
        userId: Long?,
        fundingId: Long,
        contributionId: Long,
        request: MarkTransferSentRequest,
    ): TransferSentResponse {
        // 실패 횟수가 동시 요청으로 유실되지 않도록 참여 행을 잠근다
        val contribution = contributionRepository.findByIdAndFundingIdForUpdate(contributionId, fundingId)
            ?: throw BusinessException(FundingErrorCode.CONTRIBUTION_NOT_FOUND)

        val participantUserId = contribution.participantUserId
        if (participantUserId != null) {
            if (participantUserId != userId) {
                throw BusinessException(FundingErrorCode.CONTRIBUTION_PARTICIPANT_ONLY)
            }
        } else {
            verifyGuestPassword(contribution, request.guestPassword)
        }

        contribution.markTransferSent()

        return TransferSentResponse(contribution.id, contribution.status)
    }

    private fun validateGuestIdentityAvailable(fundingId: Long, input: ParticipantInput) {
        val participantName = input.participantName ?: return
        val guestPassword = input.guestPassword ?: return
        val duplicated = contributionRepository
            .findAllByFundingIdAndParticipantUserIdIsNullAndParticipantNameAndStatusNot(
                fundingId,
                participantName,
                ContributionStatus.CANCELLED,
            )
            .any { contribution ->
                contribution.guestPasswordHash?.let { passwordEncoder.matches(guestPassword, it) } == true
            }

        if (duplicated) {
            throw BusinessException(FundingErrorCode.CONTRIBUTION_GUEST_IDENTITY_DUPLICATED)
        }
    }

    private fun verifyGuestPassword(contribution: Contribution, password: String?) {
        val now = LocalDateTime.now(clock)
        if (contribution.isGuestPasswordLocked(now)) {
            throw BusinessException(FundingErrorCode.CONTRIBUTION_PASSWORD_LOCKED)
        }

        val hash = contribution.guestPasswordHash
        if (password == null || hash == null || !passwordEncoder.matches(password, hash)) {
            contribution.recordGuestPasswordFailure(now)
            throw BusinessException(FundingErrorCode.CONTRIBUTION_PASSWORD_MISMATCH)
        }

        contribution.resetGuestPasswordFailures()
    }

    // 해시 계산과 입력 정규화는 펀딩 락을 잡기 전에 끝내 락 점유 시간을 줄인다.
    // DB 조회는 락 이후에 해야 MySQL REPEATABLE READ 스냅샷이 앞선 요청의 커밋을 보므로 이름 결정은 저장 시점에 한다
    private fun prepareParticipantInput(
        userId: Long?,
        participantName: String?,
        message: String,
        paperTemplateCode: String,
        guestPassword: String?,
    ): ParticipantInput {
        val normalizedMessage = message.normalizeOptional()
            ?: throw invalidInput("축하 메시지는 필수입니다.")
        val normalizedPaperTemplateCode = paperTemplateCode.normalizeOptional()
            ?: throw invalidInput("편지지를 선택해야 합니다.")
        if (userId == null && participantName.normalizeOptional() == null) {
            throw invalidInput("참여자 이름은 필수입니다.")
        }

        return ParticipantInput(
            participantName = participantName.normalizeOptional(),
            message = normalizedMessage,
            paperTemplateCode = normalizedPaperTemplateCode,
            guestPassword = if (userId == null) guestPassword else null,
            guestPasswordHash = if (userId == null) hashGuestPassword(guestPassword) else null,
        )
    }

    // 펀딩 단위로 잠가 남은 금액 검증과 요청 키 중복 확인을 직렬화한다
    private fun lockFunding(fundingId: Long): Funding =
        fundingRepository.findByIdForUpdate(fundingId)
            ?.takeIf { it.status != FundingStatus.CANCELLED }
            ?: throw BusinessException(FundingErrorCode.FUNDING_NOT_FOUND)

    private fun saveWithMessage(
        userId: Long?,
        fundingId: Long,
        requestKey: String,
        amount: Long,
        status: ContributionStatus,
        input: ParticipantInput,
    ): Contribution {
        val contribution = contributionRepository.save(
            Contribution(
                fundingId = fundingId,
                participantUserId = userId,
                participantName = resolveParticipantName(userId, input.participantName),
                amount = amount,
                status = status,
                guestPasswordHash = input.guestPasswordHash,
                requestKey = requestKey,
            ),
        )
        rollingPaperMessageRepository.save(
            RollingPaperMessage(
                contributionId = contribution.id,
                content = input.message,
                paperTemplateCode = input.paperTemplateCode,
            ),
        )

        return contribution
    }

    // 상세 조회의 canParticipate와 같은 기준(마감 전)으로 막는다
    private fun validateOpen(funding: Funding) {
        if (funding.status !in CONTRIBUTABLE_STATUSES || !funding.deadline.isAfter(LocalDateTime.now(clock))) {
            throw BusinessException(FundingErrorCode.FUNDING_CLOSED)
        }
    }

    // 상세 조회의 canContributeAmount와 같은 기준(마감 전 + 확정 금액 기준 미달성)으로 막는다
    private fun validateContributable(funding: Funding, amount: Long) {
        validateOpen(funding)

        val confirmedAmount = contributionRepository.sumAmountByFundingIdAndStatus(
            funding.id,
            ContributionStatus.CONFIRMED,
        )
        if (confirmedAmount >= funding.targetAmount) {
            throw BusinessException(FundingErrorCode.FUNDING_TARGET_REACHED)
        }
        // 화면에서 "남은 금액이 N원이에요"를 보여줄 수 있도록 detail에 남은 금액을 담는다
        val remainingAmount = funding.targetAmount - confirmedAmount
        if (amount > remainingAmount) {
            throw BusinessException(
                FundingErrorCode.CONTRIBUTION_AMOUNT_EXCEEDS_REMAINING,
                remainingAmount.toString(),
            )
        }
    }

    private fun resolveParticipantName(userId: Long?, participantName: String?): String {
        participantName.normalizeOptional()?.let { return it }
        if (userId == null) {
            throw invalidInput("참여자 이름은 필수입니다.")
        }

        return userRepository.findById(userId)
            .orElseThrow { BusinessException(UserErrorCode.USER_NOT_FOUND) }
            .nickname
    }

    private fun hashGuestPassword(guestPassword: String?): String {
        if (guestPassword == null || !GUEST_PASSWORD_REGEX.matches(guestPassword)) {
            throw invalidInput("비회원 비밀번호는 숫자 4자리여야 합니다.")
        }

        return checkNotNull(passwordEncoder.encode(guestPassword)) { "비밀번호 해시 생성에 실패했습니다." }
    }

    private fun payoutAccountOf(funding: Funding): ContributionPayoutAccountResponse =
        ContributionPayoutAccountResponse(
            bank = funding.payoutAccount.bank,
            bankName = funding.payoutAccount.bank.displayName,
            accountNumber = accountNumberCipher.decrypt(funding.payoutAccount.encryptedAccountNumber),
            accountHolder = funding.payoutAccount.accountHolder,
        )

    private fun String?.normalizeOptional(): String? =
        this?.trim { it.isWhitespace() || Character.isSpaceChar(it) }?.takeIf(String::isNotEmpty)

    private fun invalidInput(detail: String) = BusinessException(CommonErrorCode.INVALID_INPUT_VALUE, detail)

    private class ParticipantInput(
        val participantName: String?,
        val message: String,
        val paperTemplateCode: String,
        val guestPassword: String?,
        val guestPasswordHash: String?,
    )

    companion object {
        private val GUEST_PASSWORD_REGEX = Regex("\\d{4}")
        private val CONTRIBUTABLE_STATUSES = setOf(FundingStatus.FUNDING, FundingStatus.CLOSED)
    }
}
