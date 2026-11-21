package com.botem.domain.funding.service

import com.botem.domain.funding.dto.request.AccessGuestContributionRequest
import com.botem.domain.funding.dto.request.UpdateGuestContributionRequest
import com.botem.domain.funding.dto.response.GuestContributionResponse
import com.botem.domain.funding.dto.response.UpdatedGuestContributionResponse
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
import com.botem.global.exception.BusinessException
import com.botem.global.exception.CommonErrorCode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

@Service
class GuestContributionService(
    private val fundingRepository: FundingRepository,
    private val contributionRepository: ContributionRepository,
    private val rollingPaperMessageRepository: RollingPaperMessageRepository,
    private val accessTokenProvider: GuestContributionAccessTokenProvider,
    private val passwordVerifier: GuestContributionPasswordVerifier,
    private val rollingPaperAttachmentService: RollingPaperAttachmentService,
    private val clock: Clock,
) {

    // 인증 실패 횟수는 오류 응답을 반환해도 유지한다.
    @Transactional(noRollbackFor = [BusinessException::class])
    fun access(
        fundingId: Long,
        request: AccessGuestContributionRequest,
    ): GuestContributionResponse {
        val participantName = normalizeParticipantName(request.participantName)
        validateGuestPassword(request.guestPassword)
        lockFunding(fundingId)

        val candidates = findGuestContributions(fundingId, participantName)
        val contribution = passwordVerifier.authenticate(request.guestPassword, candidates)
        val message = rollingPaperMessageRepository.findByContributionId(contribution.id)
        val issuedToken = accessTokenProvider.issue(fundingId, contribution.id)

        return GuestContributionResponse.of(
            contribution = contribution,
            rollingPaperMessage = message,
            accessToken = issuedToken.token,
            accessTokenExpiresAt = issuedToken.expiresAt,
        )
    }

    @Transactional
    fun update(
        fundingId: Long,
        accessToken: String,
        request: UpdateGuestContributionRequest,
    ): UpdatedGuestContributionResponse {
        val principal = authenticateToken(fundingId, accessToken)
        val participantName = normalizeParticipantName(request.participantName)
        val message = normalizeOptional(request.message, MAX_MESSAGE_LENGTH, "축하 메시지는 460자 이하여야 합니다.")
        val paperTemplateCode = normalizeRequired(
            request.paperTemplateCode,
            "편지지를 선택해야 합니다.",
            MAX_PAPER_TEMPLATE_CODE_LENGTH,
        )
        val decorationCode = normalizeOptional(
            request.decorationCode,
            MAX_DECORATION_CODE_LENGTH,
            "스티커 코드는 50자 이하여야 합니다.",
        )
        val photoUrl = normalizeOptional(request.photoUrl, MAX_URL_LENGTH, "사진 URL은 2048자 이하여야 합니다.")
        val voiceUrl = normalizeOptional(request.voiceUrl, MAX_URL_LENGTH, "음성 URL은 2048자 이하여야 합니다.")
        val voiceTranscript = request.voiceTranscript?.trim()?.takeIf(String::isNotEmpty)
        validateContent(message, decorationCode, photoUrl, voiceUrl, request.voiceDurationSeconds, voiceTranscript)
        // S3 확인은 느릴 수 있어 펀딩 잠금 전에 끝낸다. 토큰은 먼저 확인해 인증 없이 S3를 호출하지 않는다
        validateUploadedAttachments(fundingId, photoUrl, voiceUrl)
        val authenticated = lock(fundingId, principal)
        val contribution = authenticated.contribution
        validateEditable(authenticated.funding)

        if (participantName != contribution.participantName) {
            validateParticipantNameAvailable(fundingId, contribution.id, participantName)
        }
        contribution.updateParticipantName(participantName)
        val rollingPaperMessage = rollingPaperMessageRepository.findByContributionId(contribution.id)
            ?: rollingPaperMessageRepository.save(
                RollingPaperMessage(
                    contributionId = contribution.id,
                    content = message,
                    paperTemplateCode = paperTemplateCode,
                    decorationCode = decorationCode,
                    photoUrl = photoUrl,
                    voiceUrl = voiceUrl,
                    voiceDurationSeconds = request.voiceDurationSeconds,
                    voiceTranscript = voiceTranscript,
                ),
            )
        rollingPaperMessage.updateContent(
            content = message,
            paperTemplateCode = paperTemplateCode,
            decorationCode = decorationCode,
            photoUrl = photoUrl,
            voiceUrl = voiceUrl,
            voiceDurationSeconds = request.voiceDurationSeconds,
            voiceTranscript = voiceTranscript,
        )

        return UpdatedGuestContributionResponse.of(contribution, rollingPaperMessage)
    }

    @Transactional
    fun cancel(fundingId: Long, accessToken: String) {
        val contribution = authenticateAndLock(fundingId, accessToken).contribution
        if (contribution.status !in CANCELLABLE_STATUSES) {
            throw BusinessException(FundingErrorCode.CONTRIBUTION_NOT_CANCELLABLE)
        }
        passwordVerifier.clear(contribution.id)
        contribution.cancel()
    }

    private fun authenticateAndLock(fundingId: Long, accessToken: String): AuthenticatedGuestContribution =
        lock(fundingId, authenticateToken(fundingId, accessToken))

    private fun authenticateToken(fundingId: Long, accessToken: String): GuestContributionAccessPrincipal {
        val principal = accessTokenProvider.parse(accessToken)
        if (principal.fundingId != fundingId) {
            throw BusinessException(FundingErrorCode.CONTRIBUTION_GUEST_ACCESS_TOKEN_INVALID)
        }

        return principal
    }

    private fun lock(fundingId: Long, principal: GuestContributionAccessPrincipal): AuthenticatedGuestContribution {
        val funding = lockFunding(fundingId)

        val contribution = contributionRepository.findByIdAndFundingIdForUpdate(
            principal.contributionId,
            fundingId,
        ) ?: throw BusinessException(FundingErrorCode.CONTRIBUTION_NOT_FOUND)
        if (contribution.participantUserId != null || contribution.status == ContributionStatus.CANCELLED) {
            throw BusinessException(FundingErrorCode.CONTRIBUTION_GUEST_ACCESS_TOKEN_INVALID)
        }
        return AuthenticatedGuestContribution(funding, contribution)
    }

    private fun validateParticipantNameAvailable(
        fundingId: Long,
        contributionId: Long,
        participantName: String,
    ) {
        val duplicated = contributionRepository
            .findAllByFundingIdAndParticipantUserIdIsNullAndParticipantNameAndStatusNot(
                fundingId,
                participantName,
                ContributionStatus.CANCELLED,
            )
            .any { it.id != contributionId }
        if (duplicated) {
            throw BusinessException(FundingErrorCode.CONTRIBUTION_GUEST_NAME_DUPLICATED)
        }
    }

    private fun lockFunding(fundingId: Long): Funding =
        fundingRepository.findByIdForUpdate(fundingId)
            ?.takeIf { it.status != FundingStatus.CANCELLED }
            ?: throw BusinessException(FundingErrorCode.FUNDING_NOT_FOUND)

    // 회원 보탬 수정과 같은 기준이다. 일반 펀딩은 조기 마감 뒤에도 원래 마감일까지 수정할 수 있다.
    private fun validateEditable(funding: Funding) {
        if (funding.status !in EDITABLE_FUNDING_STATUSES || !funding.deadline.isAfter(LocalDateTime.now(clock))) {
            throw BusinessException(FundingErrorCode.CONTRIBUTION_NOT_EDITABLE)
        }
    }

    private fun findGuestContributions(
        fundingId: Long,
        participantName: String,
    ): List<Contribution> =
        contributionRepository
            .findAllByFundingIdAndParticipantUserIdIsNullAndParticipantNameAndStatusNot(
                fundingId,
                participantName,
                ContributionStatus.CANCELLED,
            )

    private fun normalizeParticipantName(participantName: String): String {
        val normalized = participantName.trim { it.isWhitespace() || Character.isSpaceChar(it) }
        if (normalized.isEmpty() || normalized.length > MAX_PARTICIPANT_NAME_LENGTH) {
            throw BusinessException(CommonErrorCode.INVALID_INPUT_VALUE, "참여자 이름을 확인해 주세요.")
        }
        return normalized
    }

    private fun validateGuestPassword(guestPassword: String) {
        if (!GUEST_PASSWORD_REGEX.matches(guestPassword)) {
            throw BusinessException(CommonErrorCode.INVALID_INPUT_VALUE, "비회원 비밀번호는 숫자 4자리여야 합니다.")
        }
    }

    private fun normalizeRequired(value: String, detail: String, maxLength: Int): String {
        val normalized = value.trim { it.isWhitespace() || Character.isSpaceChar(it) }
        if (normalized.isEmpty() || normalized.length > maxLength) {
            throw BusinessException(CommonErrorCode.INVALID_INPUT_VALUE, detail)
        }
        return normalized
    }

    private fun normalizeOptional(value: String?, maxLength: Int, detail: String): String? {
        val normalized = value?.trim { it.isWhitespace() || Character.isSpaceChar(it) }
            ?.takeIf(String::isNotEmpty)
        if (normalized != null && normalized.length > maxLength) {
            throw BusinessException(CommonErrorCode.INVALID_INPUT_VALUE, detail)
        }
        return normalized
    }

    private fun validateContent(
        message: String?,
        decorationCode: String?,
        photoUrl: String?,
        voiceUrl: String?,
        voiceDurationSeconds: Int?,
        voiceTranscript: String?,
    ) {
        if (message == null && decorationCode == null && photoUrl == null && voiceUrl == null) {
            throw BusinessException(
                CommonErrorCode.INVALID_INPUT_VALUE,
                "축하 메시지, 스티커, 사진 또는 음성 중 하나는 필수입니다.",
            )
        }
        if ((voiceUrl == null) != (voiceDurationSeconds == null) || voiceDurationSeconds?.let { it <= 0 } == true) {
            throw BusinessException(CommonErrorCode.INVALID_INPUT_VALUE, "음성 URL과 0초보다 긴 재생 시간을 함께 입력해야 합니다.")
        }
        if (voiceTranscript != null && voiceUrl == null) {
            throw BusinessException(CommonErrorCode.INVALID_INPUT_VALUE, "음성 자막은 음성과 함께 입력해야 합니다.")
        }
        if (voiceTranscript != null && !RollingPaperMessage.hasValidVoiceTranscriptLength(voiceTranscript)) {
            throw BusinessException(
                CommonErrorCode.INVALID_INPUT_VALUE,
                "음성 자막은 ${RollingPaperMessage.MAX_VOICE_TRANSCRIPT_LENGTH}자 이하여야 합니다.",
            )
        }
    }

    private fun validateUploadedAttachments(fundingId: Long, photoUrl: String?, voiceUrl: String?) {
        photoUrl?.let {
            rollingPaperAttachmentService.validateUploadedAttachment(fundingId, RollingPaperAttachmentType.PHOTO, it)
        }
        voiceUrl?.let {
            rollingPaperAttachmentService.validateUploadedAttachment(fundingId, RollingPaperAttachmentType.VOICE, it)
        }
    }

    private data class AuthenticatedGuestContribution(
        val funding: Funding,
        val contribution: Contribution,
    )

    companion object {
        private const val MAX_PARTICIPANT_NAME_LENGTH = 50
        private const val MAX_MESSAGE_LENGTH = 460
        private const val MAX_PAPER_TEMPLATE_CODE_LENGTH = 50
        private const val MAX_DECORATION_CODE_LENGTH = 50
        private const val MAX_URL_LENGTH = 2048
        private val GUEST_PASSWORD_REGEX = Regex("\\d{4}")
        private val EDITABLE_FUNDING_STATUSES = setOf(FundingStatus.FUNDING, FundingStatus.CLOSED)
        private val CANCELLABLE_STATUSES = setOf(
            ContributionStatus.AWAITING_TRANSFER,
            ContributionStatus.MESSAGE_ONLY,
            ContributionStatus.REJECTED,
        )
    }
}
