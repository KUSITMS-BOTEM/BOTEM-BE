package com.botem.domain.funding.controller.docs

import com.botem.domain.funding.dto.response.FundingArchiveResponse
import com.botem.domain.funding.dto.response.FundingArchiveTab
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
        summary = "지난 펀딩 아카이브 조회",
        description = """
            로그인한 사용자가 개설했거나, 받는 사람이거나, 취소되지 않은 참여 이력이 있는 마감 펀딩을 조회합니다.
            생일 펀딩은 마감일이 지났거나 조기 마감했거나 마감 전에 결과를 연 경우, 서프라이즈 펀딩은 공개 시각이 지난 경우를 지난 펀딩으로 봅니다.
            취소된 펀딩과 취소된 참여 이력은 제외합니다.
            tabCounts는 선택한 탭과 무관하게 전체 아카이브 기준으로 반환합니다.
            relationship은 OPENED(내가 열었어요), RECEIVED(내가 받았어요), PARTICIPATED(내가 참여했어요) 중 하나입니다.
            years는 마감일 연도 기준 내림차순이며, 각 연도 안의 카드는 마감일 최신순입니다.
            롤링페이퍼 공개 전에는 rollingPaperCount와 representativeMessage가 모두 null입니다.
            공개된 펀딩의 representativeMessage는 취소되지 않은 롤링페이퍼 중 가장 최근 참여의 메시지 1건이며, 메시지가 없으면 null입니다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = [Content(examples = [ExampleObject(value = FUNDING_ARCHIVE_EXAMPLE)])],
        ),
        SwaggerApiResponse(
            responseCode = "400",
            description = "지원하지 않는 탭 값",
            content = [Content(examples = [ExampleObject(value = INVALID_TAB_EXAMPLE)])],
        ),
        SwaggerApiResponse(responseCode = "401", description = "인증 실패"),
    )
    fun getFundingArchive(
        @Parameter(hidden = true) userId: Long,
        @Parameter(description = "조회 탭 (기본값: ALL)") tab: FundingArchiveTab,
    ): ApiResponse<FundingArchiveResponse>

    companion object {
        private const val FUNDING_ARCHIVE_EXAMPLE = """
            {
              "success": true,
              "data": {
                "tabCounts": {
                  "all": 1,
                  "received": 0,
                  "participated": 0
                },
                "years": [
                  {
                    "year": 2026,
                    "fundingCount": 1,
                    "fundings": [
                      {
                        "fundingId": 12,
                        "relationship": "OPENED",
                        "fundingType": "BIRTHDAY",
                        "opener": {
                          "name": "서준",
                          "profileImageUrl": "https://image.example.com/profile/seojun.jpg"
                        },
                        "celebrantName": "서준",
                        "deadline": "2026-10-13T23:59:59",
                        "productName": "에어팟 프로 2",
                        "productImageUrl": "https://image.example.com/product.jpg",
                        "rollingPaperCount": 8,
                        "representativeMessage": {
                          "writerName": "하린",
                          "writerProfileImageUrl": null,
                          "content": "생일 진짜 축하해!",
                          "paperTemplateCode": "LIME_DOT_GRID",
                          "hasVoice": false
                        }
                      }
                    ]
                  }
                ]
              },
              "error": null
            }
        """
        private const val INVALID_TAB_EXAMPLE = """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "COMMON_001",
                "message": "입력값이 올바르지 않습니다.",
                "detail": "tab: 올바르지 않은 값입니다"
              }
            }
        """
    }
}
