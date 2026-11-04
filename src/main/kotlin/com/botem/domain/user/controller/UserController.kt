package com.botem.domain.user.controller

import com.botem.domain.user.controller.docs.UserControllerDocs
import com.botem.domain.user.dto.request.UpdateMyProfileRequest
import com.botem.domain.user.dto.response.MyProfileResponse
import com.botem.domain.user.service.UserService
import com.botem.global.response.ApiResponse
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/users/me/profile")
class UserController(
    private val userService: UserService,
) : UserControllerDocs {

    @GetMapping
    override fun getMyProfile(
        @AuthenticationPrincipal userId: Long,
    ): ApiResponse<MyProfileResponse> =
        ApiResponse.success(userService.getMyProfile(userId))

    @PutMapping
    override fun updateMyProfile(
        @AuthenticationPrincipal userId: Long,
        @Valid @RequestBody request: UpdateMyProfileRequest,
    ): ApiResponse<MyProfileResponse> =
        ApiResponse.success(userService.updateMyProfile(userId, request))
}
