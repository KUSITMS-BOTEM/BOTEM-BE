package com.botem.domain.friend.controller.docs

import com.botem.domain.friend.dto.request.AddFriendRequest
import com.botem.domain.friend.dto.response.FriendListResponse
import com.botem.domain.friend.dto.response.FriendProfileResponse
import com.botem.domain.friend.dto.response.FriendWishResponse
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
        summary = "친구 프로필 조회",
        description = """
            친구 목록에서 들어가는 친구 위시 프로필(FRND-PG-004)을 조회합니다. 서로 친구인 회원만 조회할 수 있습니다.
            fundings는 진행 중인 펀딩을 마감일이 가까운 순서로 반환합니다.
            친구 본인의 생일 펀딩과, 친구가 받는 사람이고 프로필 노출에 동의한 공개 전 서프라이즈를 포함합니다.
            서프라이즈는 받는 사람 본인에게는 반환하지 않습니다.
            firstParticipantName은 가장 먼저 참여한 사람의 이름이며 참여자가 없으면 null입니다.
            confirmedPercentage는 확정 금액 기준 달성률(소수점 버림)입니다.
            wishes는 최근 담은 순서이며, 진행 중인 펀딩에 쓰인 위시는 제외합니다. 친구 프로필에서는 wishesVisible이 항상 true입니다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = [Content(examples = [ExampleObject(value = FRIEND_PROFILE_EXAMPLE)])],
        ),
        SwaggerApiResponse(responseCode = "401", description = "인증 실패"),
        SwaggerApiResponse(
            responseCode = "404",
            description = "친구가 아니거나 탈퇴함",
            content = [Content(examples = [ExampleObject(value = FRIEND_NOT_FOUND_EXAMPLE)])],
        ),
    )
    fun getFriendProfile(
        @Parameter(hidden = true) userId: Long,
        @Parameter(description = "친구 회원 ID") friendId: Long,
    ): ApiResponse<FriendProfileResponse>

    @Operation(
        summary = "친구 위시 상세 조회",
        description = """
            친구 프로필에서 위시를 눌렀을 때의 상세(FRND-PG-006)를 조회합니다. 서로 친구인 회원만 조회할 수 있습니다.
            activeSurpriseFundingId는 이 친구가 받는 사람인 진행 중(공개 전) 서프라이즈의 ID입니다.
            값이 있으면 '같이 보태기', null이면 '친구 몰래 서프라이즈 열기'를 보여줍니다. 친구당 진행 중 서프라이즈는 1개입니다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = [Content(examples = [ExampleObject(value = FRIEND_WISH_EXAMPLE)])],
        ),
        SwaggerApiResponse(responseCode = "401", description = "인증 실패"),
        SwaggerApiResponse(
            responseCode = "404",
            description = "친구가 아니거나 친구의 위시가 아님",
            content = [Content(examples = [ExampleObject(value = FRIEND_NOT_FOUND_EXAMPLE)])],
        ),
    )
    fun getFriendWish(
        @Parameter(hidden = true) userId: Long,
        @Parameter(description = "친구 회원 ID") friendId: Long,
        @Parameter(description = "친구의 위시 ID") wishId: Long,
    ): ApiResponse<FriendWishResponse>

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

        const val FRIEND_PROFILE_EXAMPLE = """
            {
              "success": true,
              "data": {
                "userId": 2,
                "name": "지유",
                "profileImageUrl": "https://example.com/jiyu.jpg",
                "birthday": "10-24",
                "birthdayDday": 3,
                "fundings": [
                  {
                    "fundingId": 31,
                    "type": "SURPRISE",
                    "productName": "소니 WH-1000XM4",
                    "productImageUrl": "https://image.example.com/product.jpg",
                    "deadline": "2026-10-23T23:59:59.999999",
                    "remainingDays": 2,
                    "participantCount": 7,
                    "firstParticipantName": "민지",
                    "confirmedPercentage": 55
                  }
                ],
                "wishesVisible": true,
                "wishCount": 2,
                "wishes": [
                  {
                    "wishId": 11,
                    "productName": "모자",
                    "productImageUrl": "https://image.example.com/cap.jpg",
                    "price": 429000
                  },
                  {
                    "wishId": 10,
                    "productName": "Matin Kim 지갑",
                    "productImageUrl": "https://image.example.com/wallet.jpg",
                    "price": 327500
                  }
                ]
              },
              "error": null
            }
        """

        private const val FRIEND_WISH_EXAMPLE = """
            {
              "success": true,
              "data": {
                "wishId": 11,
                "productName": "모자",
                "productImageUrl": "https://image.example.com/cap.jpg",
                "price": 429000,
                "productUrl": "https://link.coupang.com/a/example",
                "sellerName": "쿠팡",
                "activeSurpriseFundingId": null
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
