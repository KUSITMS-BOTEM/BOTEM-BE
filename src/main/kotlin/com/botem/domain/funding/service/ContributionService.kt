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
import com.botem.domain.funding.entity.RollingPaperAttachmentType
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
    private val guestPasswordVerifier: GuestContributionPasswordVerifier,
    private val rollingPaperAttachmentService: RollingPaperAttachmentService,
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
            decorationCode = request.decorationCode,
            photoUrl = request.photoUrl,
            voiceUrl = request.voiceUrl,
            voiceDurationSeconds = request.voiceDurationSeconds,
            voiceTranscript = request.voiceTranscript,
            guestPassword = request.guestPassword,
        )
        val funding = lockFunding(fundingId)
        validateVisibleTo(funding, userId)

        contributionRepository.findByFundingIdAndRequestKey(fundingId, request.requestKey)?.let { existing ->
            if (existing.participantUserId != userId || existing.amount != request.amount) {
                throw BusinessException(FundingErrorCode.CONTRIBUTION_REQUEST_KEY_CONFLICT)
            }
            return ContributionResponse.of(existing, payoutAccountOf(funding))
        }

        validateContributable(funding, request.amount)
        validateMemberNotParticipated(fundingId, funding.extendedAt, userId)
        validateGuestIdentityAvailable(fundingId, input)
        validateUploadedAttachments(fundingId, input)
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
            decorationCode = request.decorationCode,
            photoUrl = request.photoUrl,
            voiceUrl = request.voiceUrl,
            voiceDurationSeconds = request.voiceDurationSeconds,
            voiceTranscript = request.voiceTranscript,
            guestPassword = request.guestPassword,
        )
        val funding = lockFunding(fundingId)
        validateVisibleTo(funding, userId)

        contributionRepository.findByFundingIdAndRequestKey(fundingId, request.requestKey)?.let { existing ->
            if (existing.participantUserId != userId || existing.status != ContributionStatus.MESSAGE_ONLY) {
                throw BusinessException(FundingErrorCode.CONTRIBUTION_REQUEST_KEY_CONFLICT)
            }
            return MessageContributionResponse.from(existing)
        }

        validateOpen(funding)
        validateMemberNotParticipated(fundingId, funding.extendedAt, userId)
        validateGuestIdentityAvailable(fundingId, input)
        validateUploadedAttachments(fundingId, input)
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
        // 접근 API와 같은 순서로 펀딩부터 잠가 비밀번호 실패 기록을 함께 직렬화한다.
        val funding = lockFunding(fundingId)
        val contribution = contributionRepository.findByIdAndFundingIdForUpdate(contributionId, fundingId)
            ?: throw BusinessException(FundingErrorCode.CONTRIBUTION_NOT_FOUND)

        val participantUserId = contribution.participantUserId
        if (participantUserId != null) {
            if (participantUserId != userId) {
                throw BusinessException(FundingErrorCode.CONTRIBUTION_PARTICIPANT_ONLY)
            }
        } else {
            guestPasswordVerifier.authenticate(request.guestPassword, listOf(contribution))
        }

        // 결과를 확정한 뒤에는 '못 받았대요' 보탬을 다시 확인 대기로 올려도 개설자가 처리할 수 없으므로 막는다
        if (contribution.status == ContributionStatus.REJECTED &&
            (funding.resultOpenedAt != null || funding.isSurpriseGiftPrepared())
        ) {
            throw BusinessException(FundingErrorCode.CONTRIBUTION_TRANSFER_NOT_MARKABLE)
        }
        contribution.markTransferSent()

        return TransferSentResponse(contribution.id, contribution.status)
    }

    // 한 펀딩에 금액 보태기와 메시지로 보태기를 합쳐 1인 1회 (취소한 보탬 제외).
    // 연장한 펀딩은 기존 마감일까지 송금하지 못한 회원도 다시 보탤 수 있도록 연장 이후 보탬만 센다.
    // 비회원은 로그인 없이 같은 사람을 구분할 수 없어 같은 이름 1회(validateGuestIdentityAvailable)로 대신한다
    private fun validateMemberNotParticipated(fundingId: Long, extendedAt: LocalDateTime?, userId: Long?) {
        userId ?: return
        val participated = if (extendedAt == null) {
            contributionRepository.existsByFundingIdAndParticipantUserIdAndStatusNot(
                fundingId,
                userId,
                ContributionStatus.CANCELLED,
            )
        } else {
            contributionRepository.existsByFundingIdAndParticipantUserIdAndStatusNotAndCreatedAtAfter(
                fundingId,
                userId,
                ContributionStatus.CANCELLED,
                extendedAt,
            )
        }
        if (participated) {
            throw BusinessException(FundingErrorCode.CONTRIBUTION_ALREADY_PARTICIPATED)
        }
    }

    private fun validateGuestIdentityAvailable(fundingId: Long, input: ParticipantInput) {
        val participantName = input.participantName ?: return
        val duplicated = contributionRepository
            .findAllByFundingIdAndParticipantUserIdIsNullAndParticipantNameAndStatusNot(
                fundingId,
                participantName,
                ContributionStatus.CANCELLED,
            )
            .isNotEmpty()

        if (duplicated) {
            throw BusinessException(FundingErrorCode.CONTRIBUTION_GUEST_IDENTITY_DUPLICATED)
        }
    }

    // 해시 계산과 입력 정규화는 펀딩 락을 잡기 전에 끝내 락 점유 시간을 줄인다.
    // DB 조회는 락 이후에 해야 MySQL REPEATABLE READ 스냅샷이 앞선 요청의 커밋을 보므로 이름 결정은 저장 시점에 한다
    private fun prepareParticipantInput(
        userId: Long?,
        participantName: String?,
        message: String?,
        paperTemplateCode: String,
        decorationCode: String?,
        photoUrl: String?,
        voiceUrl: String?,
        voiceDurationSeconds: Int?,
        voiceTranscript: String?,
        guestPassword: String?,
    ): ParticipantInput {
        val normalizedMessage = message.normalizeOptional()
        val normalizedPaperTemplateCode = paperTemplateCode.normalizeOptional()
            ?: throw invalidInput("편지지를 선택해야 합니다.")
        val normalizedDecorationCode = decorationCode.normalizeOptional()
        val normalizedPhotoUrl = photoUrl.normalizeOptional()
        val normalizedVoiceUrl = voiceUrl.normalizeOptional()
        if (
            normalizedMessage == null &&
            normalizedDecorationCode == null &&
            normalizedPhotoUrl == null &&
            normalizedVoiceUrl == null
        ) {
            throw invalidInput("축하 메시지, 스티커, 사진 또는 음성 중 하나는 필수입니다.")
        }
        if ((normalizedVoiceUrl == null) != (voiceDurationSeconds == null)) {
            throw invalidInput("음성 URL과 재생 시간은 함께 입력해야 합니다.")
        }
        val normalizedVoiceTranscript = voiceTranscript.normalizeOptional()
        if (normalizedVoiceTranscript != null && normalizedVoiceUrl == null) {
            throw invalidInput("음성 자막은 음성과 함께 입력해야 합니다.")
        }
        if (userId == null && participantName.normalizeOptional() == null) {
            throw invalidInput("참여자 이름은 필수입니다.")
        }

        return ParticipantInput(
            participantName = participantName.normalizeOptional(),
            message = normalizedMessage,
            paperTemplateCode = normalizedPaperTemplateCode,
            decorationCode = normalizedDecorationCode,
            photoUrl = normalizedPhotoUrl,
            voiceUrl = normalizedVoiceUrl,
            voiceDurationSeconds = voiceDurationSeconds,
            voiceTranscript = normalizedVoiceTranscript,
            guestPasswordHash = if (userId == null) hashGuestPassword(guestPassword) else null,
        )
    }

    private fun validateUploadedAttachments(fundingId: Long, input: ParticipantInput) {
        input.photoUrl?.let { photoUrl ->
            rollingPaperAttachmentService.validateUploadedAttachment(
                fundingId = fundingId,
                attachmentType = RollingPaperAttachmentType.PHOTO,
                fileUrl = photoUrl,
            )
        }
        input.voiceUrl?.let { voiceUrl ->
            rollingPaperAttachmentService.validateUploadedAttachment(
                fundingId = fundingId,
                attachmentType = RollingPaperAttachmentType.VOICE,
                fileUrl = voiceUrl,
            )
        }
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
                decorationCode = input.decorationCode,
                photoUrl = input.photoUrl,
                voiceUrl = input.voiceUrl,
                voiceDurationSeconds = input.voiceDurationSeconds,
                voiceTranscript = input.voiceTranscript,
            ),
        )

        return contribution
    }

    // 상세 조회의 canParticipate와 같은 기준(마감 전)으로 막는다
    // 공개 전 서프라이즈를 받는 사람이 직접 호출해도 주최자 계좌 등 정보를 내려주지 않는다
    private fun validateVisibleTo(funding: Funding, userId: Long?) {
        if (funding.isHiddenFromRecipient(userId, LocalDateTime.now(clock))) {
            throw BusinessException(FundingErrorCode.SURPRISE_NOT_REVEALED)
        }
    }

    private fun validateOpen(funding: Funding) {
        if (funding.status !in CONTRIBUTABLE_STATUSES || !funding.deadline.isAfter(LocalDateTime.now(clock))) {
            throw BusinessException(FundingErrorCode.FUNDING_CLOSED)
        }
    }

    // 상세 조회의 canContributeAmount와 같은 기준(마감 전 + 확정 금액 기준 미달성)으로 막는다
    private fun validateContributable(funding: Funding, amount: Long) {
        validateOpen(funding)
        if (funding.earlyClosedAt != null) {
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
        ContributionPayoutAccountResponse.of(funding, accountNumberCipher)

    private fun String?.normalizeOptional(): String? =
        this?.trim { it.isWhitespace() || Character.isSpaceChar(it) }?.takeIf(String::isNotEmpty)

    private fun invalidInput(detail: String) = BusinessException(CommonErrorCode.INVALID_INPUT_VALUE, detail)

    private class ParticipantInput(
        val participantName: String?,
        val message: String?,
        val paperTemplateCode: String,
        val decorationCode: String?,
        val photoUrl: String?,
        val voiceUrl: String?,
        val voiceDurationSeconds: Int?,
        val voiceTranscript: String?,
        val guestPasswordHash: String?,
    )

    companion object {
        private val GUEST_PASSWORD_REGEX = Regex("\\d{4}")
        private val CONTRIBUTABLE_STATUSES = setOf(FundingStatus.FUNDING, FundingStatus.CLOSED)
    }
}
