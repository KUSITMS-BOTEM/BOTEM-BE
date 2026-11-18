package com.botem.domain.funding.service

import com.botem.domain.account.service.AccountNumberCipher
import com.botem.domain.funding.dto.request.CreateContributionRequest
import com.botem.domain.funding.dto.request.MarkTransferSentRequest
import com.botem.domain.funding.dto.response.ContributionPayoutAccountResponse
import com.botem.domain.funding.dto.response.ContributionResponse
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
        // 펀딩 단위로 잠가 남은 금액 검증과 요청 키 중복 확인을 직렬화한다
        val funding = fundingRepository.findByIdForUpdate(fundingId)
            ?.takeIf { it.status != FundingStatus.CANCELLED }
            ?: throw BusinessException(FundingErrorCode.FUNDING_NOT_FOUND)

        contributionRepository.findByFundingIdAndRequestKey(fundingId, request.requestKey)?.let { existing ->
            if (existing.participantUserId != userId) {
                throw BusinessException(FundingErrorCode.CONTRIBUTION_REQUEST_KEY_CONFLICT)
            }
            return ContributionResponse.of(existing, payoutAccountOf(funding))
        }

        validateContributable(funding, request.amount)
        val message = request.message.normalizeOptional()
            ?: throw invalidInput("축하 메시지는 필수입니다.")
        val paperTemplateCode = request.paperTemplateCode.normalizeOptional()
            ?: throw invalidInput("편지지를 선택해야 합니다.")

        val contribution = contributionRepository.save(
            Contribution(
                fundingId = fundingId,
                participantUserId = userId,
                participantName = resolveParticipantName(userId, request.participantName),
                amount = request.amount,
                status = ContributionStatus.AWAITING_TRANSFER,
                guestPasswordHash = if (userId == null) hashGuestPassword(request.guestPassword) else null,
                requestKey = request.requestKey,
            ),
        )
        rollingPaperMessageRepository.save(
            RollingPaperMessage(
                contributionId = contribution.id,
                content = message,
                paperTemplateCode = paperTemplateCode,
            ),
        )

        return ContributionResponse.of(contribution, payoutAccountOf(funding))
    }

    @Transactional
    fun markTransferSent(
        userId: Long?,
        fundingId: Long,
        contributionId: Long,
        request: MarkTransferSentRequest,
    ): TransferSentResponse {
        val contribution = contributionRepository.findByIdAndFundingId(contributionId, fundingId)
            ?: throw BusinessException(FundingErrorCode.CONTRIBUTION_NOT_FOUND)

        val participantUserId = contribution.participantUserId
        if (participantUserId != null) {
            if (participantUserId != userId) {
                throw BusinessException(FundingErrorCode.CONTRIBUTION_PARTICIPANT_ONLY)
            }
        } else {
            val password = request.guestPassword
            val hash = contribution.guestPasswordHash
            if (password == null || hash == null || !passwordEncoder.matches(password, hash)) {
                throw BusinessException(FundingErrorCode.CONTRIBUTION_PASSWORD_MISMATCH)
            }
        }

        contribution.markTransferSent()

        return TransferSentResponse(contribution.id, contribution.status)
    }

    // 상세 조회의 canContributeAmount와 같은 기준(마감 전 + 확정 금액 기준 미달성)으로 막는다
    private fun validateContributable(funding: Funding, amount: Long) {
        val now = LocalDateTime.now(clock)
        if (funding.status !in CONTRIBUTABLE_STATUSES || !funding.deadline.isAfter(now)) {
            throw BusinessException(FundingErrorCode.FUNDING_CLOSED)
        }

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

    companion object {
        private val GUEST_PASSWORD_REGEX = Regex("\\d{4}")
        private val CONTRIBUTABLE_STATUSES = setOf(FundingStatus.FUNDING, FundingStatus.CLOSED)
    }
}
