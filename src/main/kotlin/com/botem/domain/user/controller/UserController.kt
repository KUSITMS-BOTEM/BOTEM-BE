package com.botem.domain.user.controller

import com.botem.domain.user.controller.docs.UserControllerDocs
import com.botem.domain.user.dto.request.UpdateMyProfileRequest
import com.botem.domain.user.dto.response.MyProfileResponse
import com.botem.domain.user.service.UserService
import com.botem.domain.user.service.UserWithdrawalService
import com.botem.global.response.ApiResponse
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Duration

@RestController
@RequestMapping("/api/users/me")
class UserController(
    private val userService: UserService,
    private val userWithdrawalService: UserWithdrawalService,
) : UserControllerDocs {

    @GetMapping("/profile")
    override fun getMyProfile(
        @AuthenticationPrincipal userId: Long,
    ): ApiResponse<MyProfileResponse> =
        ApiResponse.success(userService.getMyProfile(userId))

    @PutMapping("/profile")
    override fun updateMyProfile(
        @AuthenticationPrincipal userId: Long,
        @Valid @RequestBody request: UpdateMyProfileRequest,
    ): ApiResponse<MyProfileResponse> =
        ApiResponse.success(userService.updateMyProfile(userId, request))

    @DeleteMapping
    override fun withdraw(
        @AuthenticationPrincipal userId: Long,
    ): ResponseEntity<ApiResponse<Nothing>> {
        userWithdrawalService.withdraw(userId)

        val expiredRefreshTokenCookie = ResponseCookie.from(REFRESH_TOKEN_COOKIE, "")
            .httpOnly(true)
            .secure(true)
            .sameSite("Lax")
            .path("/api/auth")
            .maxAge(Duration.ZERO)
            .build()

        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, expiredRefreshTokenCookie.toString())
            .body(ApiResponse.success())
    }

    companion object {
        private const val REFRESH_TOKEN_COOKIE = "refreshToken"
    }
}
