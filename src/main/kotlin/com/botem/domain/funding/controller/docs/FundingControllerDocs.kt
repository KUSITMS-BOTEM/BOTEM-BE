package com.botem.domain.funding.controller.docs

import com.botem.domain.funding.dto.response.FundingResultResponse
import com.botem.global.response.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Funding", description = "펀딩 API")
interface FundingControllerDocs {

    @Operation(
        summary = "마감된 펀딩 결과 조회",
        description = """
            로그인한 펀딩 개설자가 마감된 생일 펀딩의 결과를 조회합니다.
            확정 금액은 입금 확인이 완료된 참여만 합산하고, 참여자 수는 취소된 참여를 제외합니다.
            이 API는 결과 열람 상태를 변경하지 않으며, resultOpenedAt이 null이면 아직 팡을 열지 않은 상태입니다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = [Content(examples = [ExampleObject(value = RESULT_EXAMPLE)])],
        ),
        SwaggerApiResponse(
            responseCode = "400",
            description = "생일 펀딩이 아님",
            content = [Content(examples = [ExampleObject(value = BIRTHDAY_FUNDING_REQUIRED_EXAMPLE)])],
        ),
        SwaggerApiResponse(responseCode = "401", description = "인증 실패"),
        SwaggerApiResponse(
            responseCode = "403",
            description = "펀딩 개설자가 아님",
            content = [Content(examples = [ExampleObject(value = FUNDING_OWNER_ONLY_EXAMPLE)])],
        ),
        SwaggerApiResponse(
            responseCode = "404",
            description = "펀딩 없음",
            content = [Content(examples = [ExampleObject(value = FUNDING_NOT_FOUND_EXAMPLE)])],
        ),
        SwaggerApiResponse(
            responseCode = "409",
            description = "마감 전이거나 결과를 조회할 수 없는 상태",
            content = [
                Content(
                    examples = [
                        ExampleObject(name = "FUNDING_005", summary = "마감 전", value = FUNDING_NOT_ENDED_EXAMPLE),
                        ExampleObject(
                            name = "FUNDING_006",
                            summary = "결과 조회 불가 상태",
                            value = FUNDING_RESULT_NOT_OPENABLE_EXAMPLE,
                        ),
                    ],
                ),
            ],
        ),
    )
    fun getFundingResult(
        @Parameter(hidden = true) userId: Long,
        @Parameter(description = "펀딩 ID") fundingId: Long,
    ): ApiResponse<FundingResultResponse>

    @Operation(
        summary = "펀딩 결과 팡 열기",
        description = """
            마감된 생일 펀딩의 결과를 열고 선물 준비 단계로 전환합니다.
            입금 확인 대기 참여가 남아 있으면 결과를 열 수 없습니다.
            이미 결과를 연 펀딩에 다시 요청하면 상태를 변경하지 않고 기존 결과를 반환합니다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(
            responseCode = "200",
            description = "팡 열기 성공 또는 이미 열린 결과 반환",
            content = [Content(examples = [ExampleObject(value = OPENED_RESULT_EXAMPLE)])],
        ),
        SwaggerApiResponse(
            responseCode = "400",
            description = "생일 펀딩이 아님",
            content = [Content(examples = [ExampleObject(value = BIRTHDAY_FUNDING_REQUIRED_EXAMPLE)])],
        ),
        SwaggerApiResponse(responseCode = "401", description = "인증 실패"),
        SwaggerApiResponse(
            responseCode = "403",
            description = "펀딩 개설자가 아님",
            content = [Content(examples = [ExampleObject(value = FUNDING_OWNER_ONLY_EXAMPLE)])],
        ),
        SwaggerApiResponse(
            responseCode = "404",
            description = "펀딩 없음",
            content = [Content(examples = [ExampleObject(value = FUNDING_NOT_FOUND_EXAMPLE)])],
        ),
        SwaggerApiResponse(
            responseCode = "409",
            description = "마감 전, 결과 열람 불가 상태 또는 입금 확인 대기 참여 존재",
            content = [
                Content(
                    examples = [
                        ExampleObject(name = "FUNDING_005", summary = "마감 전", value = FUNDING_NOT_ENDED_EXAMPLE),
                        ExampleObject(
                            name = "FUNDING_006",
                            summary = "결과 열람 불가 상태",
                            value = FUNDING_RESULT_NOT_OPENABLE_EXAMPLE,
                        ),
                        ExampleObject(
                            name = "FUNDING_007",
                            summary = "입금 확인 대기 참여 존재",
                            value = PENDING_CONTRIBUTION_EXISTS_EXAMPLE,
                        ),
                    ],
                ),
            ],
        ),
    )
    fun openFundingResult(
        @Parameter(hidden = true) userId: Long,
        @Parameter(description = "펀딩 ID") fundingId: Long,
    ): ApiResponse<FundingResultResponse>

    companion object {
        private const val RESULT_EXAMPLE = """
            {
              "success": true,
              "data": {
                "fundingId": 12,
                "productName": "다이슨 에어랩",
                "productImageUrl": "https://image.example.com/product.jpg",
                "targetAmount": 699000,
                "confirmedAmount": 580000,
                "achievementPercentage": 82,
                "targetReached": false,
                "participantCount": 8,
                "resultOpenedAt": null
              },
              "error": null
            }
        """
        private const val OPENED_RESULT_EXAMPLE = """
            {
              "success": true,
              "data": {
                "fundingId": 12,
                "productName": "다이슨 에어랩",
                "productImageUrl": "https://image.example.com/product.jpg",
                "targetAmount": 699000,
                "confirmedAmount": 710000,
                "achievementPercentage": 101,
                "targetReached": true,
                "participantCount": 8,
                "resultOpenedAt": "2026-11-15T00:00:00"
              },
              "error": null
            }
        """
        private const val FUNDING_NOT_FOUND_EXAMPLE = """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "FUNDING_002",
                "message": "펀딩을 찾을 수 없습니다.",
                "detail": null
              }
            }
        """
        private const val FUNDING_OWNER_ONLY_EXAMPLE = """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "FUNDING_003",
                "message": "펀딩 개설자만 접근할 수 있습니다.",
                "detail": null
              }
            }
        """
        private const val BIRTHDAY_FUNDING_REQUIRED_EXAMPLE = """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "FUNDING_004",
                "message": "생일 펀딩에서만 사용할 수 있습니다.",
                "detail": null
              }
            }
        """
        private const val FUNDING_NOT_ENDED_EXAMPLE = """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "FUNDING_005",
                "message": "펀딩이 마감된 후 결과를 확인할 수 있습니다.",
                "detail": null
              }
            }
        """
        private const val FUNDING_RESULT_NOT_OPENABLE_EXAMPLE = """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "FUNDING_006",
                "message": "현재 상태에서는 펀딩 결과를 열 수 없습니다.",
                "detail": null
              }
            }
        """
        private const val PENDING_CONTRIBUTION_EXISTS_EXAMPLE = """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "FUNDING_007",
                "message": "입금 확인 대기 중인 참여를 모두 확인한 후 결과를 열 수 있습니다.",
                "detail": null
              }
            }
        """
    }
}
