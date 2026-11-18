package com.botem.domain.funding.service

import com.botem.domain.funding.dto.request.AccessGuestContributionRequest
import com.botem.domain.funding.dto.request.UpdateGuestContributionRequest
import com.botem.domain.funding.dto.response.GuestContributionResponse
import com.botem.domain.funding.dto.response.UpdatedGuestContributionResponse
import com.botem.domain.funding.entity.Contribution
import com.botem.domain.funding.entity.ContributionStatus
import com.botem.domain.funding.entity.FundingStatus
import com.botem.domain.funding.entity.RollingPaperMessage
import com.botem.domain.funding.exception.FundingErrorCode
import com.botem.domain.funding.repository.ContributionRepository
import com.botem.domain.funding.repository.FundingRepository
import com.botem.domain.funding.repository.RollingPaperMessageRepository
import com.botem.global.exception.BusinessException
import com.botem.global.exception.CommonErrorCode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GuestContributionService(
    private val fundingRepository: FundingRepository,
    private val contributionRepository: ContributionRepository,
    private val rollingPaperMessageRepository: RollingPaperMessageRepository,
    private val accessTokenProvider: GuestContributionAccessTokenProvider,
    private val passwordVerifier: GuestContributionPasswordVerifier,
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
        val contribution = authenticateAndLock(fundingId, accessToken)
        val participantName = normalizeParticipantName(request.participantName)
        val message = normalizeRequired(request.message, "축하 메시지는 필수입니다.", MAX_MESSAGE_LENGTH)
        val paperTemplateCode = normalizeRequired(
            request.paperTemplateCode,
            "편지지를 선택해야 합니다.",
            MAX_PAPER_TEMPLATE_CODE_LENGTH,
        )

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
                ),
            )
        rollingPaperMessage.updateTextContent(message, paperTemplateCode)

        return UpdatedGuestContributionResponse.of(contribution, rollingPaperMessage)
    }

    @Transactional
    fun cancel(fundingId: Long, accessToken: String) {
        val contribution = authenticateAndLock(fundingId, accessToken)
        if (contribution.status !in CANCELLABLE_STATUSES) {
            throw BusinessException(FundingErrorCode.CONTRIBUTION_NOT_CANCELLABLE)
        }
        passwordVerifier.clear(contribution.id)
        contribution.cancel()
    }

    private fun authenticateAndLock(fundingId: Long, accessToken: String): Contribution {
        val principal = accessTokenProvider.parse(accessToken)
        if (principal.fundingId != fundingId) {
            throw BusinessException(FundingErrorCode.CONTRIBUTION_GUEST_ACCESS_TOKEN_INVALID)
        }
        lockFunding(fundingId)

        val contribution = contributionRepository.findByIdAndFundingIdForUpdate(
            principal.contributionId,
            fundingId,
        ) ?: throw BusinessException(FundingErrorCode.CONTRIBUTION_NOT_FOUND)
        if (contribution.participantUserId != null || contribution.status == ContributionStatus.CANCELLED) {
            throw BusinessException(FundingErrorCode.CONTRIBUTION_GUEST_ACCESS_TOKEN_INVALID)
        }
        return contribution
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

    private fun lockFunding(fundingId: Long) {
        fundingRepository.findByIdForUpdate(fundingId)
            ?.takeIf { it.status != FundingStatus.CANCELLED }
            ?: throw BusinessException(FundingErrorCode.FUNDING_NOT_FOUND)
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

    companion object {
        private const val MAX_PARTICIPANT_NAME_LENGTH = 50
        private const val MAX_MESSAGE_LENGTH = 460
        private const val MAX_PAPER_TEMPLATE_CODE_LENGTH = 50
        private val GUEST_PASSWORD_REGEX = Regex("\\d{4}")
        private val CANCELLABLE_STATUSES = setOf(
            ContributionStatus.AWAITING_TRANSFER,
            ContributionStatus.MESSAGE_ONLY,
            ContributionStatus.REJECTED,
        )
    }
}
