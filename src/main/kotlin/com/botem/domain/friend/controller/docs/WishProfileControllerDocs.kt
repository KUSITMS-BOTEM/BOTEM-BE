package com.botem.domain.friend.controller.docs

import com.botem.domain.friend.dto.response.FriendProfileResponse
import com.botem.global.exception.ErrorCode
import com.botem.global.response.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Wish Profile", description = "위시 프로필 API")
interface WishProfileControllerDocs {

    @Operation(
        summary = "위시 프로필 조회",
        description = """
            위시 프로필 링크 `/@{wishProfileKey}`로 들어온 방문자에게 프로필을 보여줍니다(FRND-PG-004 · FRND-PG-005).
            Authorization 헤더 없이 호출하면 비로그인으로 조회하고, 헤더의 토큰이 만료되었거나 유효하지 않으면 401을 반환합니다.
            응답 형식은 친구 프로필 조회와 같습니다.
            위시는 로그인한 친구와 프로필 주인 본인에게만 보여줍니다. 그 외 방문자는 wishesVisible이 false이고 wishes는 빈 배열이며,
            wishCount로 잠긴 위시 개수만 표시합니다.
            서프라이즈 펀딩은 로그인한 방문자에게만 반환하고, 받는 사람(프로필 주인) 본인과 비로그인 방문자에게는 반환하지 않습니다.
            생일 펀딩은 비로그인 방문자도 볼 수 있습니다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = [Content(examples = [ExampleObject(value = FriendControllerDocs.FRIEND_PROFILE_EXAMPLE)])],
        ),
        SwaggerApiResponse(
            responseCode = "401",
            description = "보낸 토큰이 만료되었거나 유효하지 않음",
            content = [Content(examples = [ExampleObject(value = EXPIRED_TOKEN_EXAMPLE)])],
        ),
        SwaggerApiResponse(
            responseCode = "404",
            description = "위시 프로필 주인이 없거나 탈퇴함",
            content = [Content(examples = [ExampleObject(value = WISH_PROFILE_NOT_FOUND_EXAMPLE)])],
        ),
    )
    fun getWishProfile(
        @Parameter(hidden = true) userId: Long?,
        @Parameter(hidden = true) tokenErrorCode: ErrorCode?,
        @Parameter(description = "위시 프로필 키") wishProfileKey: String,
    ): ApiResponse<FriendProfileResponse>

    companion object {
        private const val WISH_PROFILE_NOT_FOUND_EXAMPLE = """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "FRIEND_003",
                "message": "위시 프로필을 찾을 수 없습니다.",
                "detail": null
              }
            }
        """
        private const val EXPIRED_TOKEN_EXAMPLE = """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "AUTH_003",
                "message": "만료된 토큰입니다.",
                "detail": null
              }
            }
        """
    }
}
