package com.botem.domain.funding.controller.docs

import com.botem.domain.funding.dto.response.ClosedFundingListResponse
import com.botem.global.response.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "My Funding", description = "내 펀딩 API")
interface MyFundingControllerDocs {

    @Operation(
        summary = "마감된 펀딩 목록 조회",
        description = """
            로그인한 사용자가 연 펀딩 중 마감된 펀딩을 마감일이 최근인 순서로 조회합니다. 취소된 펀딩은 제외합니다.
            생일 펀딩은 마감일이 지났거나 조기 마감했거나 마감 전에 결과를 연 경우, 서프라이즈 펀딩은 공개 시각이 지난 경우를 마감으로 봅니다.
            진행 중인 펀딩은 홈 펀딩 카드에서 조회합니다.
            targetReached는 확정 금액이 목표 금액 이상인지 여부입니다.
            participantCount는 취소된 참여를 제외하며, 로그인 참여자는 계정 기준으로 중복을 제거합니다.
            recipientName은 서프라이즈 펀딩의 받는 사람 이름이며 생일 펀딩은 null입니다.
            giftVerificationStatus는 개설자가 선물을 받는 생일 펀딩에서만 반환합니다.
            팡!을 연 뒤 인증 전이면 REQUIRED, 선물 인증을 마치면 COMPLETED, 팡!을 열기 전이거나 서프라이즈 펀딩이면 null입니다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = [Content(examples = [ExampleObject(value = CLOSED_FUNDINGS_EXAMPLE)])],
        ),
        SwaggerApiResponse(responseCode = "401", description = "인증 실패"),
    )
    fun getClosedFundings(
        @Parameter(hidden = true) userId: Long,
    ): ApiResponse<ClosedFundingListResponse>

    companion object {
        private const val CLOSED_FUNDINGS_EXAMPLE = """
            {
              "success": true,
              "data": {
                "fundings": [
                  {
                    "fundingId": 12,
                    "type": "BIRTHDAY",
                    "productName": "에어팟 프로 2",
                    "productImageUrl": "https://image.example.com/product.jpg",
                    "deadline": "2025-10-13T23:59:59.999999",
                    "targetReached": true,
                    "participantCount": 8,
                    "recipientName": null,
                    "giftVerificationStatus": "REQUIRED"
                  },
                  {
                    "fundingId": 9,
                    "type": "SURPRISE",
                    "productName": "에어팟 프로 2",
                    "productImageUrl": "https://image.example.com/product.jpg",
                    "deadline": "2025-10-10T23:59:59.999999",
                    "targetReached": false,
                    "participantCount": 6,
                    "recipientName": "서준",
                    "giftVerificationStatus": null
                  }
                ]
              },
              "error": null
            }
        """
    }
}
