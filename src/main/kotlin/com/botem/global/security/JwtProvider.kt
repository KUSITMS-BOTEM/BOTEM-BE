package com.botem.global.security

import com.botem.global.config.JwtProperties
import com.botem.global.exception.AuthErrorCode
import com.botem.global.exception.BusinessException
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.io.Decoders
import io.jsonwebtoken.security.Keys
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.Date
import javax.crypto.SecretKey

@Component
class JwtProvider(
    private val jwtProperties: JwtProperties,
) {

    private val secretKey: SecretKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtProperties.secret))

    fun createAccessToken(userId: Long): String =
        createToken(userId, ACCESS_TOKEN_TYPE, jwtProperties.accessTokenExpiration)

    fun createRefreshToken(userId: Long): String =
        createToken(userId, REFRESH_TOKEN_TYPE, jwtProperties.refreshTokenExpiration)

    fun getUserIdFromAccessToken(token: String): Long = parseUserId(token, ACCESS_TOKEN_TYPE)

    fun getUserIdFromRefreshToken(token: String): Long = parseUserId(token, REFRESH_TOKEN_TYPE)

    private fun createToken(userId: Long, tokenType: String, expiration: Duration): String {
        val now = Date()

        return Jwts.builder()
            .subject(userId.toString())
            .claim(TOKEN_TYPE_CLAIM, tokenType)
            .issuedAt(now)
            .expiration(Date(now.time + expiration.toMillis()))
            .signWith(secretKey)
            .compact()
    }

    private fun parseUserId(token: String, expectedTokenType: String): Long {
        val claims = try {
            Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .payload
        } catch (exception: ExpiredJwtException) {
            throw BusinessException(AuthErrorCode.EXPIRED_TOKEN)
        } catch (exception: JwtException) {
            throw BusinessException(AuthErrorCode.INVALID_TOKEN)
        } catch (exception: IllegalArgumentException) {
            throw BusinessException(AuthErrorCode.INVALID_TOKEN)
        }

        if (claims[TOKEN_TYPE_CLAIM] != expectedTokenType) {
            throw BusinessException(AuthErrorCode.INVALID_TOKEN)
        }

        return claims.subject.toLong()
    }

    companion object {
        private const val TOKEN_TYPE_CLAIM = "type"
        private const val ACCESS_TOKEN_TYPE = "access"
        private const val REFRESH_TOKEN_TYPE = "refresh"
    }
}
