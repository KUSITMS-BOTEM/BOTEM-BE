package com.botem.domain.friend.controller

import com.botem.domain.friend.controller.docs.WishProfileControllerDocs
import com.botem.domain.friend.dto.response.FriendProfileResponse
import com.botem.domain.friend.service.FriendProfileService
import com.botem.global.exception.BusinessException
import com.botem.global.exception.ErrorCode
import com.botem.global.jwt.JwtAuthenticationFilter
import com.botem.global.response.ApiResponse
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/wish-profiles")
class WishProfileController(
    private val friendProfileService: FriendProfileService,
) : WishProfileControllerDocs {

    // 비로그인도 열 수 있지만, 토큰을 보냈는데 만료·위조면 비로그인으로 낮추지 않고 401로 재발급을 유도한다
    @GetMapping("/{wishProfileKey}")
    override fun getWishProfile(
        @AuthenticationPrincipal userId: Long?,
        @RequestAttribute(JwtAuthenticationFilter.ERROR_CODE_ATTRIBUTE, required = false) tokenErrorCode: ErrorCode?,
        @PathVariable wishProfileKey: String,
    ): ApiResponse<FriendProfileResponse> {
        if (tokenErrorCode != null) {
            throw BusinessException(tokenErrorCode)
        }

        return ApiResponse.success(friendProfileService.getWishProfile(userId, wishProfileKey))
    }
}
