package com.botem.domain.funding.service

import com.botem.domain.funding.entity.Contribution
import com.botem.domain.funding.entity.GuestContributionAccessAttempt
import com.botem.domain.funding.exception.FundingErrorCode
import com.botem.domain.funding.repository.GuestContributionAccessAttemptRepository
import com.botem.global.exception.BusinessException
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.LocalDateTime

@Component
class GuestContributionPasswordVerifier(
    private val accessAttemptRepository: GuestContributionAccessAttemptRepository,
    private val clock: Clock,
) {

    private val passwordEncoder = BCryptPasswordEncoder()

    fun authenticate(password: String?, candidates: List<Contribution>): Contribution {
        val suppliedPassword = password.orEmpty()
        val matchingContribution = candidates.firstOrNull { contribution ->
            contribution.guestPasswordHash?.let { passwordEncoder.matches(suppliedPassword, it) } == true
        }

        if (matchingContribution == null) {
            if (candidates.isEmpty()) {
                passwordEncoder.matches(suppliedPassword, DUMMY_PASSWORD_HASH)
            } else {
                candidates.forEach(::recordFailure)
            }
            throw BusinessException(FundingErrorCode.CONTRIBUTION_PASSWORD_MISMATCH)
        }

        val attempt = accessAttemptRepository.findByContributionIdForUpdate(matchingContribution.id)
        if (attempt?.isLocked(LocalDateTime.now(clock)) == true) {
            throw BusinessException(FundingErrorCode.CONTRIBUTION_PASSWORD_LOCKED)
        }
        if (attempt != null) {
            accessAttemptRepository.delete(attempt)
        }
        return matchingContribution
    }

    fun clear(contributionId: Long) {
        accessAttemptRepository.findByContributionIdForUpdate(contributionId)
            ?.let(accessAttemptRepository::delete)
    }

    private fun recordFailure(contribution: Contribution) {
        val now = LocalDateTime.now(clock)
        val attempt = accessAttemptRepository.findByContributionIdForUpdate(contribution.id)
        if (attempt?.isLocked(now) == true) {
            throw BusinessException(FundingErrorCode.CONTRIBUTION_PASSWORD_LOCKED)
        }

        val accessAttempt = attempt ?: accessAttemptRepository.save(
            GuestContributionAccessAttempt(contribution.id),
        )
        accessAttempt.recordFailure(now)
    }

    companion object {
        // 등록되지 않은 이름도 BCrypt 비교를 한 번 수행해 이름 존재 여부가 응답 시간으로 드러나지 않게 한다.
        private const val DUMMY_PASSWORD_HASH =
            "\$2a\$10\$7EqJtq98hPqEX7fNZaFWoO5lA9GhYF9.K1L8M4Yf8hGd6A7sM8j6K"
    }
}
