package com.botem.domain.auth.controller

import com.botem.domain.auth.controller.docs.AuthControllerDocs
import com.botem.domain.auth.dto.request.KakaoLoginRequest
import com.botem.domain.auth.dto.response.AccessTokenResponse
import com.botem.domain.auth.dto.response.KakaoLoginResponse
import com.botem.domain.auth.dto.response.KakaoLoginUriResponse
import com.botem.domain.auth.service.AuthService
import com.botem.global.exception.AuthErrorCode
import com.botem.global.exception.BusinessException
import com.botem.global.response.ApiResponse
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService,
    private val authCookieFactory: AuthCookieFactory,
) : AuthControllerDocs {

    @GetMapping("/kakao/login-uri")
    override fun getKakaoLoginUri(
        @RequestParam(required = false) redirectUri: String?,
    ): ApiResponse<KakaoLoginUriResponse> =
        ApiResponse.success(authService.getKakaoLoginUri(redirectUri))

    @PostMapping("/kakao/login")
    override fun kakaoLogin(
        @Valid @RequestBody request: KakaoLoginRequest,
    ): ResponseEntity<ApiResponse<KakaoLoginResponse>> {
        val result = authService.kakaoLogin(request.code, request.redirectUri)

        return createTokenResponse(
            result.tokens.refreshToken,
            KakaoLoginResponse(accessToken = result.tokens.accessToken, isNewUser = result.isNewUser),
        )
    }

    @PostMapping("/reissue")
    override fun reissue(
        @CookieValue(AuthCookieFactory.REFRESH_TOKEN_COOKIE, required = false) refreshToken: String?,
    ): ResponseEntity<ApiResponse<AccessTokenResponse>> {
        val token = refreshToken ?: throw BusinessException(AuthErrorCode.INVALID_TOKEN)

        val tokens = authService.reissue(token)

        return createTokenResponse(tokens.refreshToken, AccessTokenResponse(tokens.accessToken))
    }

    @PostMapping("/logout")
    override fun logout(
        @CookieValue(AuthCookieFactory.REFRESH_TOKEN_COOKIE, required = false) refreshToken: String?,
    ): ResponseEntity<ApiResponse<Nothing>> {
        refreshToken?.let(authService::logout)

        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, *authCookieFactory.expire())
            .body(ApiResponse.success())
    }

    private fun <T> createTokenResponse(refreshToken: String, body: T): ResponseEntity<ApiResponse<T>> =
        ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, *authCookieFactory.issue(refreshToken))
            .body(ApiResponse.success(body))
}
