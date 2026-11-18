package com.botem.domain.funding.service

import com.botem.domain.funding.entity.Contribution
import com.botem.domain.funding.entity.ContributionStatus
import com.botem.domain.funding.entity.GuestContributionAccessAttempt
import com.botem.domain.funding.exception.FundingErrorCode
import com.botem.domain.funding.repository.GuestContributionAccessAttemptRepository
import com.botem.global.exception.BusinessException
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.test.util.ReflectionTestUtils.setField
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class GuestContributionPasswordVerifierTest {

    private val accessAttemptRepository = mock(GuestContributionAccessAttemptRepository::class.java)
    private val clock = MutableClock(Instant.parse("2026-11-18T03:00:00Z"))
    private val verifier = GuestContributionPasswordVerifier(accessAttemptRepository, clock)

    @Test
    fun `등록되지 않은 이름은 실패 이력을 만들지 않는다`() {
        assertError(FundingErrorCode.CONTRIBUTION_PASSWORD_MISMATCH) {
            verifier.authenticate("1234", emptyList())
        }

        verifyNoInteractions(accessAttemptRepository)
    }

    @Test
    fun `등록된 보탬은 다섯 번 실패하면 잠기고 10분 뒤 올바른 비밀번호로 접근할 수 있다`() {
        val contribution = contribution(CONTRIBUTION_ID, "1234")
        val attempt = GuestContributionAccessAttempt(CONTRIBUTION_ID)
        var saved = false
        given(accessAttemptRepository.findByContributionIdForUpdate(CONTRIBUTION_ID))
            .willAnswer { if (saved) attempt else null }
        given(accessAttemptRepository.save(any(GuestContributionAccessAttempt::class.java))).willAnswer {
            saved = true
            attempt
        }

        repeat(GuestContributionAccessAttempt.MAX_FAILURES) {
            assertError(FundingErrorCode.CONTRIBUTION_PASSWORD_MISMATCH) {
                verifier.authenticate("0000", listOf(contribution))
            }
        }
        assertNotNull(attempt.lockedUntil)
        assertError(FundingErrorCode.CONTRIBUTION_PASSWORD_LOCKED) {
            verifier.authenticate("1234", listOf(contribution))
        }

        clock.advance(GuestContributionAccessAttempt.LOCK_DURATION.plusSeconds(1))
        assertEquals(contribution, verifier.authenticate("1234", listOf(contribution)))
        verify(accessAttemptRepository).delete(attempt)
    }

    @Test
    fun `같은 이름의 기존 보탬 중 인증된 보탬의 실패 이력만 삭제한다`() {
        val first = contribution(CONTRIBUTION_ID, "1111")
        val second = contribution(OTHER_CONTRIBUTION_ID, "2222")
        val firstAttempt = GuestContributionAccessAttempt(CONTRIBUTION_ID).also { it.recordFailure(NOW) }
        val secondAttempt = GuestContributionAccessAttempt(OTHER_CONTRIBUTION_ID).also { it.recordFailure(NOW) }
        given(accessAttemptRepository.findByContributionIdForUpdate(OTHER_CONTRIBUTION_ID)).willReturn(secondAttempt)

        assertEquals(second, verifier.authenticate("2222", listOf(first, second)))

        verify(accessAttemptRepository).delete(secondAttempt)
        verify(accessAttemptRepository, never()).delete(firstAttempt)
    }

    private fun contribution(id: Long, password: String) = Contribution(
        fundingId = FUNDING_ID,
        participantUserId = null,
        participantName = "민지",
        amount = 10_000L,
        status = ContributionStatus.AWAITING_TRANSFER,
        guestPasswordHash = BCryptPasswordEncoder().encode(password),
        requestKey = "request-$id",
    ).also { setField(it, "id", id) }

    private fun assertError(errorCode: FundingErrorCode, block: () -> Unit) {
        val exception = assertFailsWith<BusinessException>(block = block)
        assertEquals(errorCode, exception.errorCode)
    }

    private class MutableClock(
        private var current: Instant,
    ) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock = this

        override fun instant(): Instant = current

        fun advance(duration: Duration) {
            current = current.plus(duration)
        }
    }

    companion object {
        private const val FUNDING_ID = 12L
        private const val CONTRIBUTION_ID = 31L
        private const val OTHER_CONTRIBUTION_ID = 32L
        private val NOW = java.time.LocalDateTime.of(2026, 11, 18, 12, 0)
    }
}
