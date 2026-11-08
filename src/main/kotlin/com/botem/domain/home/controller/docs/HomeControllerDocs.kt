package com.botem.domain.home.controller.docs

import com.botem.domain.home.dto.response.HomeFundingListResponse
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
    }
}
