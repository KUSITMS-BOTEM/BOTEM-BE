package com.botem.domain.funding.service

import com.botem.domain.funding.dto.request.AccessGuestContributionRequest
import com.botem.domain.funding.dto.response.GuestContributionResponse
import com.botem.domain.funding.entity.Contribution
import com.botem.domain.funding.entity.ContributionStatus
import com.botem.domain.funding.entity.FundingStatus
import com.botem.domain.funding.entity.GuestContributionAccessAttempt
import com.botem.domain.funding.exception.FundingErrorCode
import com.botem.domain.funding.repository.ContributionRepository
import com.botem.domain.funding.repository.FundingRepository
import com.botem.domain.funding.repository.GuestContributionAccessAttemptRepository
import com.botem.domain.funding.repository.RollingPaperMessageRepository
import com.botem.global.exception.BusinessException
import com.botem.global.exception.CommonErrorCode
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

@Service
class GuestContributionService(
    private val fundingRepository: FundingRepository,
    private val contributionRepository: ContributionRepository,
    private val rollingPaperMessageRepository: RollingPaperMessageRepository,
    private val accessAttemptRepository: GuestContributionAccessAttemptRepository,
    private val accessTokenProvider: GuestContributionAccessTokenProvider,
    private val clock: Clock,
) {

    private val passwordEncoder = BCryptPasswordEncoder()

    // 인증 실패 횟수는 오류 응답을 반환해도 유지한다.
    @Transactional(noRollbackFor = [BusinessException::class])
    fun access(
        fundingId: Long,
        request: AccessGuestContributionRequest,
    ): GuestContributionResponse {
        val participantName = normalizeParticipantName(request.participantName)
        validateGuestPassword(request.guestPassword)
        lockFunding(fundingId)

        val now = LocalDateTime.now(clock)
        val attempt = accessAttemptRepository.findByFundingIdAndParticipantNameForUpdate(
            fundingId,
            participantName,
        )
        if (attempt?.isLocked(now) == true) {
            throw BusinessException(FundingErrorCode.CONTRIBUTION_PASSWORD_LOCKED)
        }

        val contribution = findMatchingContribution(fundingId, participantName, request.guestPassword)
        if (contribution == null) {
            recordFailure(attempt, fundingId, participantName, now)
            throw BusinessException(FundingErrorCode.CONTRIBUTION_PASSWORD_MISMATCH)
        }

        attempt?.reset()
        val message = rollingPaperMessageRepository.findByContributionId(contribution.id)
            ?: throw BusinessException(FundingErrorCode.CONTRIBUTION_NOT_FOUND)
        val issuedToken = accessTokenProvider.issue(fundingId, contribution.id)

        return GuestContributionResponse.of(
            contribution = contribution,
            rollingPaperMessage = message,
            accessToken = issuedToken.token,
            accessTokenExpiresAt = issuedToken.expiresAt,
        )
    }

    private fun lockFunding(fundingId: Long) {
        fundingRepository.findByIdForUpdate(fundingId)
            ?.takeIf { it.status != FundingStatus.CANCELLED }
            ?: throw BusinessException(FundingErrorCode.FUNDING_NOT_FOUND)
    }

    private fun findMatchingContribution(
        fundingId: Long,
        participantName: String,
        guestPassword: String,
    ): Contribution? =
        contributionRepository
            .findAllByFundingIdAndParticipantUserIdIsNullAndParticipantNameAndStatusNot(
                fundingId,
                participantName,
                ContributionStatus.CANCELLED,
            )
            .firstOrNull { contribution ->
                contribution.guestPasswordHash?.let { passwordEncoder.matches(guestPassword, it) } == true
            }

    private fun recordFailure(
        attempt: GuestContributionAccessAttempt?,
        fundingId: Long,
        participantName: String,
        now: LocalDateTime,
    ) {
        val accessAttempt = attempt ?: accessAttemptRepository.save(
            GuestContributionAccessAttempt(
                fundingId = fundingId,
                participantName = participantName,
            ),
        )
        accessAttempt.recordFailure(now)
    }

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

    companion object {
        private const val MAX_PARTICIPANT_NAME_LENGTH = 50
        private val GUEST_PASSWORD_REGEX = Regex("\\d{4}")
    }
}
