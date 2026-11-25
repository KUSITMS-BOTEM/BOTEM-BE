package com.botem.domain.funding.service

import com.botem.domain.funding.exception.FundingErrorCode
import com.botem.global.exception.BusinessException
import com.botem.global.jwt.JwtProvider
import com.botem.global.property.GuestContributionTokenProperties
import com.botem.global.property.JwtProperties
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.io.Decoders
import io.jsonwebtoken.security.Keys
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Date
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

    @Test
    fun `로그인 토큰 타입은 비회원 접근 토큰으로 사용할 수 없다`() {
        val clock = MutableClock(Instant.parse("2030-01-01T00:00:00Z"))
        val provider = provider(clock)
        val token = customToken(type = "access", subject = CONTRIBUTION_ID.toString(), fundingId = FUNDING_ID)

        assertError(FundingErrorCode.CONTRIBUTION_GUEST_ACCESS_TOKEN_INVALID) {
            provider.parse(token)
        }
    }

    @Test
    fun `회원 Access 토큰은 비회원 접근 토큰으로 사용할 수 없다`() {
        val provider = provider(Clock.systemUTC())
        val memberAccessToken = JwtProvider(
            JwtProperties(
                secret = SECRET,
                accessTokenExpiration = Duration.ofHours(1),
                refreshTokenExpiration = Duration.ofDays(7),
            ),
        ).createAccessToken(CONTRIBUTION_ID)

        assertError(FundingErrorCode.CONTRIBUTION_GUEST_ACCESS_TOKEN_INVALID) {
            provider.parse(memberAccessToken)
        }
    }

    @Test
    fun `issuer가 다른 비회원 접근 토큰은 거부한다`() {
        val provider = provider(MutableClock(Instant.parse("2030-01-01T00:00:00Z")))
        val token = customToken(issuer = "other-service")

        assertError(FundingErrorCode.CONTRIBUTION_GUEST_ACCESS_TOKEN_INVALID) {
            provider.parse(token)
        }
    }

    @Test
    fun `audience가 다른 비회원 접근 토큰은 거부한다`() {
        val provider = provider(MutableClock(Instant.parse("2030-01-01T00:00:00Z")))
        val token = customToken(audience = "other-audience")

        assertError(FundingErrorCode.CONTRIBUTION_GUEST_ACCESS_TOKEN_INVALID) {
            provider.parse(token)
        }
    }

    @Test
    fun `필수 식별자가 없는 비회원 접근 토큰은 거부한다`() {
        val clock = MutableClock(Instant.parse("2030-01-01T00:00:00Z"))
        val provider = provider(clock)
        val token = customToken(type = "guest-contribution-access", subject = null, fundingId = null)

        assertError(FundingErrorCode.CONTRIBUTION_GUEST_ACCESS_TOKEN_INVALID) {
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
            tokenProperties = GuestContributionTokenProperties(
                accessTokenExpiration = Duration.ofMinutes(10),
                issuer = ISSUER,
                audience = AUDIENCE,
            ),
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

    private fun customToken(
        type: String = "guest-contribution-access",
        subject: String? = CONTRIBUTION_ID.toString(),
        fundingId: Long? = FUNDING_ID,
        issuer: String = ISSUER,
        audience: String = AUDIENCE,
    ): String {
        val builder = Jwts.builder()
            .issuer(issuer)
            .audience()
            .add(audience)
            .and()
            .claim("type", type)
            .issuedAt(Date.from(Instant.parse("2030-01-01T00:00:00Z")))
            .expiration(Date.from(Instant.parse("2030-01-01T00:10:00Z")))
        subject?.let(builder::subject)
        fundingId?.let { builder.claim("fundingId", it) }
        return builder
            .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET)))
            .compact()
    }

    companion object {
        private const val FUNDING_ID = 12L
        private const val CONTRIBUTION_ID = 31L
        private const val ISSUER = "botem"
        private const val AUDIENCE = "guest-contribution"
        private const val SECRET = "dGVzdC1zZWNyZXQta2V5LWZvci1ndWVzdC1jb250cmlidXRpb24tMTIzNDU2Nzg5MA=="
        private const val OTHER_SECRET = "b3RoZXItc2VjcmV0LWtleS1mb3ItZ3Vlc3QtY29udHJpYnV0aW9uLTEyMzQ1Njc4OTA="
    }
}
