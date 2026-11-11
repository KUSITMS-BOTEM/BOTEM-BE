package com.botem.domain.user.controller

import com.botem.domain.auth.controller.AuthCookieFactory
import com.botem.domain.user.controller.docs.UserControllerDocs
import com.botem.domain.user.dto.request.CreateProfileImageUploadUrlRequest
import com.botem.domain.user.dto.request.RegisterOnboardingRequest
import com.botem.domain.user.dto.request.UpdateMyProfileRequest
import com.botem.domain.user.dto.response.MyProfileResponse
import com.botem.domain.user.dto.response.ProfileImageUploadUrlResponse
import com.botem.domain.user.service.OnboardingService
import com.botem.domain.user.service.ProfileImageService
import com.botem.domain.user.service.UserService
import com.botem.domain.user.service.UserWithdrawalService
import com.botem.global.response.ApiResponse
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/users/me")
class UserController(
    private val userService: UserService,
    private val userWithdrawalService: UserWithdrawalService,
    private val onboardingService: OnboardingService,
    private val profileImageService: ProfileImageService,
    private val authCookieFactory: AuthCookieFactory,
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

    @PostMapping("/profile-image/upload-url")
    override fun createProfileImageUploadUrl(
        @AuthenticationPrincipal userId: Long,
        @Valid @RequestBody request: CreateProfileImageUploadUrlRequest,
    ): ApiResponse<ProfileImageUploadUrlResponse> =
        ApiResponse.success(profileImageService.createUploadUrl(userId, request.contentType))

    @PostMapping("/onboarding")
    override fun completeOnboarding(
        @AuthenticationPrincipal userId: Long,
        @Valid @RequestBody request: RegisterOnboardingRequest,
    ): ApiResponse<Nothing> {
        onboardingService.completeOnboarding(userId, request)

        return ApiResponse.success()
    }

    @DeleteMapping
    override fun withdraw(
        @AuthenticationPrincipal userId: Long,
    ): ResponseEntity<ApiResponse<Nothing>> {
        userWithdrawalService.withdraw(userId)

        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, *authCookieFactory.expire())
            .body(ApiResponse.success())
    }
}
