package com.botem.domain.auth.controller

import com.botem.domain.auth.dto.KakaoLoginRequest
import com.botem.domain.auth.dto.KakaoLoginUriResponse
import com.botem.domain.auth.dto.ReissueRequest
import com.botem.domain.auth.dto.TokenResponse
import com.botem.domain.auth.service.AuthService
import com.botem.global.response.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Auth", description = "인증 API")
@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService,
) {

    @Operation(summary = "카카오 로그인 URI 조회")
    @GetMapping("/kakao/login-uri")
    fun getKakaoLoginUri(): ApiResponse<KakaoLoginUriResponse> =
        ApiResponse.success(authService.getKakaoLoginUri())

    @Operation(summary = "카카오 로그인", description = "카카오 인가 코드로 로그인하고, 처음 로그인하면 회원가입합니다.")
    @PostMapping("/kakao/login")
    fun kakaoLogin(@Valid @RequestBody request: KakaoLoginRequest): ApiResponse<TokenResponse> =
        ApiResponse.success(authService.kakaoLogin(request.code))

    @Operation(summary = "토큰 재발급", description = "Refresh 토큰으로 Access 토큰과 Refresh 토큰을 재발급합니다.")
    @PostMapping("/reissue")
    fun reissue(@Valid @RequestBody request: ReissueRequest): ApiResponse<TokenResponse> =
        ApiResponse.success(authService.reissue(request.refreshToken))
}
