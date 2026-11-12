package com.botem.domain.friend.controller.docs

import com.botem.domain.friend.dto.request.AddFriendRequest
import com.botem.domain.friend.dto.response.FriendListResponse
import com.botem.global.response.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.parameters.RequestBody
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Friend", description = "친구 API")
interface FriendControllerDocs {

    @Operation(
        summary = "친구 맺기",
        description = """
            친구의 위시 프로필(초대 링크)에서 로그인한 회원과 프로필 주인을 서로 친구로 등록합니다. 요청·수락 단계는 없습니다.
            wishProfileKey는 위시 프로필 주소 `/@{wishProfileKey}`의 값입니다.
            이미 친구이거나 내 위시 프로필이면 아무것도 하지 않고 성공을 응답합니다.
        """,
    )
    @RequestBody(
        content = [Content(examples = [ExampleObject(value = """{ "wishProfileKey": "8f0c6d779fae47f3a979302f2782d20f" }""")])],
    )
    @ApiResponses(
        SwaggerApiResponse(
            responseCode = "200",
            description = "친구 맺기 성공",
            content = [Content(examples = [ExampleObject(value = SUCCESS_EXAMPLE)])],
        ),
        SwaggerApiResponse(responseCode = "400", description = "요청 값 오류"),
        SwaggerApiResponse(responseCode = "401", description = "인증 실패"),
        SwaggerApiResponse(
            responseCode = "404",
            description = "위시 프로필 주인이 없거나 탈퇴함",
            content = [Content(examples = [ExampleObject(value = FRIEND_TARGET_NOT_FOUND_EXAMPLE)])],
        ),
    )
    fun addFriend(
        @Parameter(hidden = true) userId: Long,
        request: AddFriendRequest,
    ): ApiResponse<Nothing>

    @Operation(
        summary = "친구 목록 조회",
        description = """
            내 친구 목록을 조회합니다.
            upcomingBirthdayFriends: 생일이 오늘부터 60일 이내인 친구를 생일이 가까운 순으로 담습니다. 생일이 없는 친구는 제외됩니다.
            friends: 전체 친구를 이름 가나다순으로 담고, totalCount는 전체 친구 수입니다.
            name은 회원 이름이며 온보딩 전이라 이름이 없으면 카카오 닉네임입니다.
            birthdayDday는 다음 생일까지 남은 일수(오늘이 생일이면 0)이며 생일이 없으면 null입니다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = [Content(examples = [ExampleObject(value = FRIEND_LIST_EXAMPLE)])],
        ),
        SwaggerApiResponse(responseCode = "401", description = "인증 실패"),
    )
    fun getFriends(
        @Parameter(hidden = true) userId: Long,
    ): ApiResponse<FriendListResponse>

    @Operation(
        summary = "친구 끊기",
        description = "친구 관계를 양쪽 모두에서 삭제합니다.",
    )
    @ApiResponses(
        SwaggerApiResponse(
            responseCode = "200",
            description = "친구 끊기 성공",
            content = [Content(examples = [ExampleObject(value = SUCCESS_EXAMPLE)])],
        ),
        SwaggerApiResponse(responseCode = "401", description = "인증 실패"),
        SwaggerApiResponse(
            responseCode = "404",
            description = "친구가 아님",
            content = [Content(examples = [ExampleObject(value = FRIEND_NOT_FOUND_EXAMPLE)])],
        ),
    )
    fun deleteFriend(
        @Parameter(hidden = true) userId: Long,
        @Parameter(description = "친구 회원 ID") friendId: Long,
    ): ApiResponse<Nothing>

    companion object {
        private const val SUCCESS_EXAMPLE =
            """{ "success": true, "data": null, "error": null }"""

        private const val FRIEND_LIST_EXAMPLE = """
            {
              "success": true,
              "data": {
                "totalCount": 2,
                "upcomingBirthdayFriends": [
                  {
                    "userId": 2,
                    "name": "민지",
                    "profileImageUrl": "https://example.com/minji.jpg",
                    "birthday": "10-24",
                    "birthdayDday": 3,
                    "wishCount": 3
                  }
                ],
                "friends": [
                  {
                    "userId": 2,
                    "name": "민지",
                    "profileImageUrl": "https://example.com/minji.jpg",
                    "birthday": "10-24",
                    "birthdayDday": 3,
                    "wishCount": 3
                  },
                  {
                    "userId": 3,
                    "name": "서준",
                    "profileImageUrl": null,
                    "birthday": null,
                    "birthdayDday": null,
                    "wishCount": 0
                  }
                ]
              },
              "error": null
            }
        """

        private const val FRIEND_TARGET_NOT_FOUND_EXAMPLE = """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "FRIEND_001",
                "message": "친구를 맺을 회원을 찾을 수 없습니다.",
                "detail": null
              }
            }
        """

        private const val FRIEND_NOT_FOUND_EXAMPLE = """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "FRIEND_002",
                "message": "친구가 아닌 회원입니다.",
                "detail": null
              }
            }
        """
    }
}
