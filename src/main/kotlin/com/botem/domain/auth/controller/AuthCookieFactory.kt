package com.botem.domain.auth.controller

import com.botem.global.property.AuthCookieProperties
import com.botem.global.property.JwtProperties
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class AuthCookieFactory(
    private val jwtProperties: JwtProperties,
    private val authCookieProperties: AuthCookieProperties,
) {

    fun issue(refreshToken: String): Array<String> = arrayOf(
        refreshTokenCookie(refreshToken, jwtProperties.refreshTokenExpiration),
        loginStatusCookie("true", jwtProperties.refreshTokenExpiration),
    )

    fun expire(): Array<String> = arrayOf(
        refreshTokenCookie("", Duration.ZERO),
        loginStatusCookie("", Duration.ZERO),
    )

    private fun refreshTokenCookie(value: String, maxAge: Duration): String =
        ResponseCookie.from(REFRESH_TOKEN_COOKIE, value)
            .httpOnly(true)
            .secure(true)
            .sameSite("Lax")
            .path("/api/auth")
            .maxAge(maxAge)
            .build()
            .toString()

    // 프론트 Middleware가 로그인 여부만 판단하는 용도로, 토큰을 담지 않으므로 HttpOnly를 적용하지 않는다
    private fun loginStatusCookie(value: String, maxAge: Duration): String =
        ResponseCookie.from(LOGIN_STATUS_COOKIE, value)
            .secure(true)
            .sameSite("Lax")
            .domain(authCookieProperties.domain)
            .path("/")
            .maxAge(maxAge)
            .build()
            .toString()

    companion object {
        const val REFRESH_TOKEN_COOKIE = "refreshToken"
        private const val LOGIN_STATUS_COOKIE = "isLoggedIn"
    }
}
