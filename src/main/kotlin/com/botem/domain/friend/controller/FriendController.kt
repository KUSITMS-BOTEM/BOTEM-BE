package com.botem.domain.friend.controller

import com.botem.domain.friend.controller.docs.FriendControllerDocs
import com.botem.domain.friend.dto.request.AddFriendRequest
import com.botem.domain.friend.dto.response.FriendListResponse
import com.botem.domain.friend.service.FriendService
import com.botem.global.response.ApiResponse
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/friends")
class FriendController(
    private val friendService: FriendService,
) : FriendControllerDocs {

    @PostMapping
    override fun addFriend(
        @AuthenticationPrincipal userId: Long,
        @Valid @RequestBody request: AddFriendRequest,
    ): ApiResponse<Nothing> {
        friendService.addFriend(userId, request.wishProfileKey)

        return ApiResponse.success()
    }

    @GetMapping
    override fun getFriends(
        @AuthenticationPrincipal userId: Long,
    ): ApiResponse<FriendListResponse> =
        ApiResponse.success(friendService.getFriends(userId))

    @DeleteMapping("/{friendId}")
    override fun deleteFriend(
        @AuthenticationPrincipal userId: Long,
        @PathVariable friendId: Long,
    ): ApiResponse<Nothing> {
        friendService.deleteFriend(userId, friendId)

        return ApiResponse.success()
    }
}
