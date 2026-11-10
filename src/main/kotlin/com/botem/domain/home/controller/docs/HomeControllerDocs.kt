package com.botem.domain.home.controller.docs

import com.botem.domain.home.dto.response.GiftVerificationBannerResponse
import com.botem.domain.home.dto.response.GiftVerificationTargetListResponse
import com.botem.domain.home.dto.response.HomeFundingListResponse
import com.botem.domain.home.dto.response.HomeNoFundingResponse
import com.botem.global.response.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Home", description = "홈 API")
interface HomeControllerDocs {

    @Operation(
        summary = "홈 펀딩 카드 조회",
        description = """
            로그인한 사용자의 마감 후 팡을 열지 않은 펀딩과 진행 중인 펀딩 카드를 조회합니다.
            마감 카드를 먼저 반환하고, 진행 중 카드는 마감일 또는 서프라이즈 공개일이 가까운 순서로 반환합니다.
            확정 및 확인 대기 달성률과 진행 중인 펀딩 개수를 함께 반환합니다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = [Content(examples = [ExampleObject(value = SUCCESS_EXAMPLE)])],
        ),
        SwaggerApiResponse(responseCode = "401", description = "인증 실패"),
    )
    fun getHomeFundings(
        @Parameter(hidden = true) userId: Long,
    ): ApiResponse<HomeFundingListResponse>

    @Operation(
        summary = "펀딩 없는 홈 상태 조회",
        description = """
            홈에 표시할 펀딩이 없는 사용자의 위시 추천 또는 빈 홈 상태를 조회합니다.
            위시는 최신 등록 순으로 반환하며, 생일이 30일 이내이면 생일 D-day를 함께 반환합니다.
            홈에 표시할 펀딩이 존재하면 409 Conflict를 반환합니다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = [
                Content(
                    examples = [
                        ExampleObject(
                            name = "위시 추천 상태",
                            value = WISH_RECOMMENDATION_EXAMPLE,
                        ),
                        ExampleObject(
                            name = "빈 홈 상태",
                            value = EMPTY_HOME_EXAMPLE,
                        ),
                    ],
                ),
            ],
        ),
        SwaggerApiResponse(responseCode = "401", description = "인증 실패"),
        SwaggerApiResponse(
            responseCode = "409",
            description = "홈에 표시할 펀딩 존재",
            content = [Content(examples = [ExampleObject(value = FUNDING_EXISTS_EXAMPLE)])],
        ),
    )
    fun getNoFundingHome(
        @Parameter(hidden = true) userId: Long,
        @Parameter(description = "위시 페이지 번호(0부터 시작)", example = "0") page: Int,
        @Parameter(description = "페이지 크기(1~20)", example = "10") size: Int,
    ): ApiResponse<HomeNoFundingResponse>

    @Operation(
        summary = "선물 인증 대기 배너 조회",
        description = """
            로그인한 사용자가 인증해야 하는 선물의 개수와 홈 배너 노출 여부를 조회합니다.
            인증 대기 건수가 0이면 visible은 false입니다.
            홈의 펀딩 존재 여부와 관계없이 조회할 수 있습니다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = [
                Content(
                    examples = [
                        ExampleObject(name = "배너 노출", value = GIFT_VERIFICATION_BANNER_VISIBLE_EXAMPLE),
                        ExampleObject(name = "배너 숨김", value = GIFT_VERIFICATION_BANNER_HIDDEN_EXAMPLE),
                    ],
                ),
            ],
        ),
        SwaggerApiResponse(responseCode = "401", description = "인증 실패"),
    )
    fun getGiftVerificationBanner(
        @Parameter(hidden = true) userId: Long,
    ): ApiResponse<GiftVerificationBannerResponse>

    @Operation(
        summary = "선물 인증 대상 목록 조회",
        description = """
            로그인한 사용자가 인증해야 하는 선물 목록을 오래된 펀딩 마감 순으로 조회합니다.
            완료된 인증과 취소된 참여는 제외하며, 30일이 지나도 인증 대상을 자동으로 제외하지 않습니다.
            현재 메시지만 남긴 참여는 참여 인원에 포함합니다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = [Content(examples = [ExampleObject(value = GIFT_VERIFICATION_TARGETS_EXAMPLE)])],
        ),
        SwaggerApiResponse(responseCode = "401", description = "인증 실패"),
    )
    fun getGiftVerificationTargets(
        @Parameter(hidden = true) userId: Long,
    ): ApiResponse<GiftVerificationTargetListResponse>

    companion object {
        private const val SUCCESS_EXAMPLE = """
            {
              "success": true,
              "data": {
                "fundingCards": [
                  {
                    "fundingId": 12,
                    "cardType": "CLOSED_UNOPENED",
                    "fundingType": "BIRTHDAY",
                    "productName": "다이슨 에어랩",
                    "productImageUrl": "https://image.example.com/product.jpg",
                    "remainingDays": null,
                    "confirmedPercentage": 100,
                    "pendingPercentage": 0,
                    "pendingContributionCount": 0
                  },
                  {
                    "fundingId": 15,
                    "cardType": "IN_PROGRESS",
                    "fundingType": "SURPRISE",
                    "productName": "무선 헤드폰",
                    "productImageUrl": "https://image.example.com/headphone.jpg",
                    "remainingDays": 7,
                    "confirmedPercentage": 55,
                    "pendingPercentage": 15,
                    "pendingContributionCount": 2
                  }
                ],
                "activeFundingCount": 1,
                "canOpenFunding": true
              },
              "error": null
            }
        """
        private const val WISH_RECOMMENDATION_EXAMPLE = """
            {
              "success": true,
              "data": {
                "state": "WISH_RECOMMENDATION",
                "nickname": "지유",
                "birthdayDday": 23,
                "wishes": {
                  "content": [
                    {
                      "wishId": 21,
                      "productName": "다이슨 에어랩",
                      "productImageUrl": "https://image.example.com/product.jpg",
                      "price": 699000,
                      "productUrl": "https://shop.example.com/product"
                    }
                  ],
                  "page": 0,
                  "size": 10,
                  "totalElements": 12,
                  "hasNext": true
                }
              },
              "error": null
            }
        """
        private const val EMPTY_HOME_EXAMPLE = """
            {
              "success": true,
              "data": {
                "state": "EMPTY",
                "nickname": "지유",
                "birthdayDday": null,
                "wishes": {
                  "content": [],
                  "page": 0,
                  "size": 10,
                  "totalElements": 0,
                  "hasNext": false
                }
              },
              "error": null
            }
        """
        private const val FUNDING_EXISTS_EXAMPLE = """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "HOME_001",
                "message": "홈에 표시할 펀딩이 존재합니다."
              }
            }
        """
        private const val GIFT_VERIFICATION_BANNER_VISIBLE_EXAMPLE = """
            {
              "success": true,
              "data": {
                "visible": true,
                "pendingCount": 2
              },
              "error": null
            }
        """
        private const val GIFT_VERIFICATION_BANNER_HIDDEN_EXAMPLE = """
            {
              "success": true,
              "data": {
                "visible": false,
                "pendingCount": 0
              },
              "error": null
            }
        """
        private const val GIFT_VERIFICATION_TARGETS_EXAMPLE = """
            {
              "success": true,
              "data": {
                "pendingCount": 1,
                "targets": [
                  {
                    "giftVerificationId": 7,
                    "fundingId": 12,
                    "productName": "다이슨 에어랩",
                    "productImageUrl": "https://image.example.com/product.jpg",
                    "fundingDeadline": "2026-11-01T23:59:59",
                    "participantCount": 6,
                    "replacementProductName": null
                  }
                ]
              },
              "error": null
            }
        """
    }
}
