package com.botem.domain.auth.controller

import com.botem.domain.auth.controller.docs.AuthControllerDocs
import com.botem.domain.auth.dto.request.KakaoLoginRequest
import com.botem.domain.auth.dto.request.ReissueRequest
import com.botem.domain.auth.dto.response.KakaoLoginUriResponse
import com.botem.domain.auth.dto.response.TokenResponse
import com.botem.domain.auth.service.AuthService
import com.botem.global.response.ApiResponse
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService,
) : AuthControllerDocs {

    @GetMapping("/kakao/login-uri")
    override fun getKakaoLoginUri(): ApiResponse<KakaoLoginUriResponse> =
        ApiResponse.success(authService.getKakaoLoginUri())

    @PostMapping("/kakao/login")
    override fun kakaoLogin(@Valid @RequestBody request: KakaoLoginRequest): ApiResponse<TokenResponse> =
        ApiResponse.success(authService.kakaoLogin(request.code))

    @PostMapping("/reissue")
    override fun reissue(@Valid @RequestBody request: ReissueRequest): ApiResponse<TokenResponse> =
        ApiResponse.success(authService.reissue(request.refreshToken))
}
