package com.botem.domain.funding.service

import com.botem.domain.funding.exception.FundingErrorCode
import com.botem.global.exception.BusinessException
import com.botem.global.property.GuestContributionTokenProperties
import com.botem.global.property.JwtProperties
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GuestContributionAccessTokenProviderTest {

    @Test
    fun `발급한 토큰에서 펀딩과 보탬 ID를 확인한다`() {
        val provider = provider(MutableClock(Instant.parse("2030-01-01T00:00:00.750Z")))

        val issued = provider.issue(FUNDING_ID, CONTRIBUTION_ID)
        val principal = provider.parse(issued.token)

        assertEquals(FUNDING_ID, principal.fundingId)
        assertEquals(CONTRIBUTION_ID, principal.contributionId)
        assertEquals(Instant.parse("2030-01-01T00:10:00Z"), issued.expiresAt)
    }

    @Test
    fun `위조된 토큰은 거부한다`() {
        val provider = provider(MutableClock(Instant.parse("2030-01-01T00:00:00Z")))
        val otherProvider = provider(MutableClock(Instant.parse("2030-01-01T00:00:00Z")), OTHER_SECRET)
        val token = otherProvider.issue(FUNDING_ID, CONTRIBUTION_ID).token

        assertError(FundingErrorCode.CONTRIBUTION_GUEST_ACCESS_TOKEN_INVALID) {
            provider.parse(token)
        }
    }

    @Test
    fun `만료된 토큰은 만료 오류로 거부한다`() {
        val clock = MutableClock(Instant.parse("2030-01-01T00:00:00Z"))
        val provider = provider(clock)
        val token = provider.issue(FUNDING_ID, CONTRIBUTION_ID).token
        clock.advance(Duration.ofMinutes(11))

        assertError(FundingErrorCode.CONTRIBUTION_GUEST_ACCESS_TOKEN_EXPIRED) {
            provider.parse(token)
        }
    }

    private fun provider(clock: Clock, secret: String = SECRET) =
        GuestContributionAccessTokenProvider(
            jwtProperties = JwtProperties(
                secret = secret,
                accessTokenExpiration = Duration.ofHours(1),
                refreshTokenExpiration = Duration.ofDays(7),
            ),
            tokenProperties = GuestContributionTokenProperties(Duration.ofMinutes(10)),
            clock = clock,
        )

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

    private fun assertError(errorCode: FundingErrorCode, block: () -> Unit) {
        val exception = assertFailsWith<BusinessException>(block = block)
        assertEquals(errorCode, exception.errorCode)
    }

    companion object {
        private const val FUNDING_ID = 12L
        private const val CONTRIBUTION_ID = 31L
        private const val SECRET = "dGVzdC1zZWNyZXQta2V5LWZvci1ndWVzdC1jb250cmlidXRpb24tMTIzNDU2Nzg5MA=="
        private const val OTHER_SECRET = "b3RoZXItc2VjcmV0LWtleS1mb3ItZ3Vlc3QtY29udHJpYnV0aW9uLTEyMzQ1Njc4OTA="
    }
}
