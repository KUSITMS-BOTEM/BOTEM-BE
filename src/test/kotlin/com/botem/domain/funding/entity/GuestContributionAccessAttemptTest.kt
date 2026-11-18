package com.botem.domain.funding.entity

import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GuestContributionAccessAttemptTest {

    @Test
    fun `다섯 번 실패하면 잠기고 잠금 시간이 지나면 다시 실패 횟수를 센다`() {
        val attempt = GuestContributionAccessAttempt(CONTRIBUTION_ID)

        repeat(GuestContributionAccessAttempt.MAX_FAILURES) {
            attempt.recordFailure(NOW)
        }

        assertEquals(CONTRIBUTION_ID, attempt.contributionId)
        assertEquals(0L, attempt.id)
        assertTrue(attempt.isLocked(NOW))
        assertEquals(NOW.plus(GuestContributionAccessAttempt.LOCK_DURATION), attempt.lockedUntil)

        val afterLock = NOW.plus(GuestContributionAccessAttempt.LOCK_DURATION).plusSeconds(1)
        attempt.recordFailure(afterLock)

        assertFalse(attempt.isLocked(afterLock))
        assertEquals(1, attempt.failedCount)
        assertNull(attempt.lockedUntil)
    }

    @Test
    fun `실패 기록을 초기화한다`() {
        val attempt = GuestContributionAccessAttempt(CONTRIBUTION_ID)
        attempt.recordFailure(NOW)

        attempt.reset()

        assertEquals(0, attempt.failedCount)
        assertNull(attempt.lockedUntil)
    }

    companion object {
        private const val CONTRIBUTION_ID = 31L
        private val NOW = LocalDateTime.of(2026, 11, 18, 12, 0)
    }
}
