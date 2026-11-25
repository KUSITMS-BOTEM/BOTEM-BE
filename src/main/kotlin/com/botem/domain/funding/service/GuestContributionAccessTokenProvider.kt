package com.botem.domain.funding.service

import com.botem.domain.funding.exception.FundingErrorCode
import com.botem.global.exception.BusinessException
import com.botem.global.property.GuestContributionTokenProperties
import com.botem.global.property.JwtProperties
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.io.Decoders
import io.jsonwebtoken.security.Keys
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey

@Component
class GuestContributionAccessTokenProvider(
    jwtProperties: JwtProperties,
    private val tokenProperties: GuestContributionTokenProperties,
    private val clock: Clock,
) {

    private val secretKey: SecretKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtProperties.secret))

    fun issue(fundingId: Long, contributionId: Long): IssuedGuestContributionAccessToken {
        val issuedAt = Instant.now(clock).truncatedTo(ChronoUnit.SECONDS)
        val expiresAt = issuedAt.plus(tokenProperties.accessTokenExpiration).truncatedTo(ChronoUnit.SECONDS)
        val token = Jwts.builder()
            .id(UUID.randomUUID().toString())
            .subject(contributionId.toString())
            .issuer(tokenProperties.issuer)
            .audience()
            .add(tokenProperties.audience)
            .and()
            .claim(TOKEN_TYPE_CLAIM, TOKEN_TYPE)
            .claim(FUNDING_ID_CLAIM, fundingId)
            .issuedAt(Date.from(issuedAt))
            .expiration(Date.from(expiresAt))
            .signWith(secretKey)
            .compact()

        return IssuedGuestContributionAccessToken(token, expiresAt)
    }

    fun parse(token: String): GuestContributionAccessPrincipal {
        val claims = try {
            Jwts.parser()
                .verifyWith(secretKey)
                .requireIssuer(tokenProperties.issuer)
                .requireAudience(tokenProperties.audience)
                .clock { Date.from(Instant.now(clock)) }
                .build()
                .parseSignedClaims(token)
                .payload
        } catch (exception: ExpiredJwtException) {
            throw BusinessException(FundingErrorCode.CONTRIBUTION_GUEST_ACCESS_TOKEN_EXPIRED)
        } catch (exception: JwtException) {
            throw BusinessException(FundingErrorCode.CONTRIBUTION_GUEST_ACCESS_TOKEN_INVALID)
        } catch (exception: IllegalArgumentException) {
            throw BusinessException(FundingErrorCode.CONTRIBUTION_GUEST_ACCESS_TOKEN_INVALID)
        }

        if (claims[TOKEN_TYPE_CLAIM] != TOKEN_TYPE) {
            throw BusinessException(FundingErrorCode.CONTRIBUTION_GUEST_ACCESS_TOKEN_INVALID)
        }

        val fundingId = (claims[FUNDING_ID_CLAIM] as? Number)?.toLong()
        val contributionId = claims.subject?.toLongOrNull()
        if (fundingId == null || contributionId == null) {
            throw BusinessException(FundingErrorCode.CONTRIBUTION_GUEST_ACCESS_TOKEN_INVALID)
        }

        return GuestContributionAccessPrincipal(
            fundingId = fundingId,
            contributionId = contributionId,
        )
    }

    companion object {
        const val HEADER_NAME = "X-Guest-Contribution-Token"
        private const val TOKEN_TYPE_CLAIM = "type"
        private const val FUNDING_ID_CLAIM = "fundingId"
        private const val TOKEN_TYPE = "guest-contribution-access"
    }
}

data class IssuedGuestContributionAccessToken(
    val token: String,
    val expiresAt: Instant,
)

data class GuestContributionAccessPrincipal(
    val fundingId: Long,
    val contributionId: Long,
)
