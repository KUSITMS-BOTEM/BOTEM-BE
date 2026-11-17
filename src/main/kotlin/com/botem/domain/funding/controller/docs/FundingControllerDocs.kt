package com.botem.domain.funding.controller.docs

import com.botem.domain.funding.dto.request.CreateBirthdayFundingRequest
import com.botem.domain.funding.dto.response.CreateFundingResponse
import com.botem.domain.funding.dto.response.FundingDetailResponse
import com.botem.domain.funding.dto.response.FundingResultResponse
import com.botem.global.exception.ErrorCode
import com.botem.global.response.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.parameters.RequestBody
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Funding", description = "펀딩 API")
interface FundingControllerDocs {

    @Operation(
        summary = "일반 생일 펀딩 개설",
        description = """
            로그인 사용자의 위시 상품과 받을 계좌를 스냅샷으로 저장하여 일반 생일 펀딩을 개설합니다.
            payoutAccountId를 생략하면 기본 계좌를 사용합니다. 마감일은 선택한 날짜의 마지막 시각으로 저장됩니다.
            진행 중인 일반 펀딩과 공개 대기 중인 서프라이즈를 합쳐 최대 5개까지 개설할 수 있습니다.
        """,
    )
    @RequestBody(
        required = true,
        content = [Content(examples = [ExampleObject(value = CREATE_FUNDING_REQUEST_EXAMPLE)])],
    )
    @ApiResponses(
        SwaggerApiResponse(
            responseCode = "201",
            description = "개설 성공",
            content = [Content(examples = [ExampleObject(value = CREATE_FUNDING_RESPONSE_EXAMPLE)])],
        ),
        SwaggerApiResponse(
            responseCode = "400",
            description = "제목, 목표 금액, 마감일, 사연 또는 공약 입력 오류",
            content = [Content(examples = [ExampleObject(value = INVALID_INPUT_EXAMPLE)])],
        ),
        SwaggerApiResponse(responseCode = "401", description = "인증 실패"),
        SwaggerApiResponse(
            responseCode = "404",
            description = "위시 또는 받을 계좌 없음",
            content = [
                Content(
                    examples = [
                        ExampleObject(name = "WISH_002", summary = "위시 없음", value = WISH_NOT_FOUND_EXAMPLE),
                        ExampleObject(
                            name = "PAYOUT_ACCOUNT_001",
                            summary = "받을 계좌 없음",
                            value = PAYOUT_ACCOUNT_NOT_FOUND_EXAMPLE,
                        ),
                    ],
                ),
            ],
        ),
        SwaggerApiResponse(
            responseCode = "409",
            description = "사용 중인 위시, 사용할 수 없는 계좌 또는 진행 중 펀딩 개수 초과",
            content = [
                Content(
                    examples = [
                        ExampleObject(name = "WISH_003", summary = "사용 중인 위시", value = WISH_IN_FUNDING_EXAMPLE),
                        ExampleObject(
                            name = "PAYOUT_ACCOUNT_005",
                            summary = "사용할 수 없는 계좌",
                            value = PAYOUT_ACCOUNT_NOT_AVAILABLE_EXAMPLE,
                        ),
                        ExampleObject(
                            name = "FUNDING_019",
                            summary = "진행 중 펀딩 5개",
                            value = ACTIVE_FUNDING_LIMIT_EXCEEDED_EXAMPLE,
                        ),
                    ],
                ),
            ],
        ),
    )
    fun createBirthdayFunding(
        @Parameter(hidden = true) userId: Long,
        request: CreateBirthdayFundingRequest,
    ): ApiResponse<CreateFundingResponse>

    @Operation(
        summary = "참여자용 펀딩 상세 조회",
        description = """
            공유 링크로 펀딩 정보와 진행 상태를 조회합니다. 로그인하지 않아도 조회할 수 있습니다.
            금액은 노출하지 않고, 확정 달성률(입금 확인 완료)과 확인 대기 달성률만 반환합니다.
            참여자 목록은 취소된 참여를 제외하고 첫 참여 순서대로 반환합니다. 로그인 참여자는 한 번만,
            비로그인 참여는 각각 한 명으로 집계하며 메시지만 남긴 참여자도 포함합니다.
            order는 참여 순서(1부터)이며 역할 배지 문구는 이 값으로 결정합니다.
            displayStatus는 IN_PROGRESS(진행 중), TARGET_REACHED(목표 금액 달성), CLOSED(마감),
            GIFT_PREPARING(선물 준비 중), COMPLETED(인증 완료) 중 하나입니다.
            목표 금액 달성 후에는 canContributeAmount가 false, 마감 후에는 canParticipate도 false입니다.
            remainingDays는 마감일까지 남은 일수(마감일 당일 0)이며 마감 후에는 null입니다.
            ownerPreview는 로그인한 개설자 본인이 조회한 경우 true입니다. 취소된 펀딩은 404를 반환합니다.
            Authorization 헤더 없이 호출하면 비로그인으로 조회하고, 헤더의 토큰이 만료되었거나 유효하지 않으면 401을 반환합니다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = [Content(examples = [ExampleObject(value = DETAIL_EXAMPLE)])],
        ),
        SwaggerApiResponse(
            responseCode = "401",
            description = "보낸 토큰이 만료되었거나 유효하지 않음",
            content = [Content(examples = [ExampleObject(value = EXPIRED_TOKEN_EXAMPLE)])],
        ),
        SwaggerApiResponse(
            responseCode = "404",
            description = "펀딩 없음 또는 취소된 펀딩",
            content = [Content(examples = [ExampleObject(value = FUNDING_NOT_FOUND_EXAMPLE)])],
        ),
    )
    fun getFundingDetail(
        @Parameter(hidden = true) userId: Long?,
        @Parameter(hidden = true) tokenErrorCode: ErrorCode?,
        @Parameter(description = "펀딩 ID") fundingId: Long,
    ): ApiResponse<FundingDetailResponse>

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
        private const val CREATE_FUNDING_REQUEST_EXAMPLE = """
            {
              "wishId": 3,
              "payoutAccountId": 2,
              "title": "생일에 에어팟 받고 싶어요",
              "targetAmount": 350000,
              "deadline": "2026-11-30",
              "story": "올해 생일에는 오래 사용한 이어폰을 바꾸고 싶어요.",
              "promise": "선물을 받으면 인증 사진을 올릴게요."
            }
        """
        private const val CREATE_FUNDING_RESPONSE_EXAMPLE = """
            {
              "success": true,
              "data": {
                "fundingId": 12,
                "type": "BIRTHDAY",
                "status": "FUNDING",
                "shareUrl": "https://botem.me/fundings/12"
              },
              "error": null
            }
        """
        private const val INVALID_INPUT_EXAMPLE = """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "COMMON_001",
                "message": "입력값이 올바르지 않습니다.",
                "detail": "마감일은 내일부터 선택할 수 있습니다."
              }
            }
        """
        private const val WISH_NOT_FOUND_EXAMPLE = """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "WISH_002",
                "message": "위시를 찾을 수 없습니다.",
                "detail": null
              }
            }
        """
        private const val PAYOUT_ACCOUNT_NOT_FOUND_EXAMPLE = """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "PAYOUT_ACCOUNT_001",
                "message": "받을 계좌를 찾을 수 없습니다.",
                "detail": null
              }
            }
        """
        private const val WISH_IN_FUNDING_EXAMPLE = """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "WISH_003",
                "message": "진행 중인 펀딩에 사용 중인 위시입니다.",
                "detail": null
              }
            }
        """
        private const val PAYOUT_ACCOUNT_NOT_AVAILABLE_EXAMPLE = """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "PAYOUT_ACCOUNT_005",
                "message": "현재 사용할 수 없는 받을 계좌입니다.",
                "detail": null
              }
            }
        """
        private const val ACTIVE_FUNDING_LIMIT_EXCEEDED_EXAMPLE = """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "FUNDING_019",
                "message": "진행 중인 펀딩은 최대 5개까지 개설할 수 있습니다.",
                "detail": null
              }
            }
        """
        private const val DETAIL_EXAMPLE = """
            {
              "success": true,
              "data": {
                "fundingId": 12,
                "type": "BIRTHDAY",
                "title": "생일에 에어팟 받고 싶어요",
                "product": {
                  "name": "에어팟 프로 3",
                  "imageUrl": "https://image.example.com/product.jpg",
                  "price": 300000,
                  "url": "https://www.coupang.com/vp/products/1"
                },
                "story": "올해 생일에는 오래 사용한 이어폰을 바꾸고 싶어요.",
                "promise": "선물을 받으면 인증 사진을 올릴게요.",
                "deadline": "2026-11-30T23:59:59",
                "remainingDays": 12,
                "confirmedPercentage": 40,
                "pendingPercentage": 10,
                "participantCount": 2,
                "participants": [
                  { "order": 1, "name": "지유", "profileImageUrl": "https://image.example.com/profile.jpg" },
                  { "order": 2, "name": "익명 친구", "profileImageUrl": null }
                ],
                "displayStatus": "IN_PROGRESS",
                "canContributeAmount": true,
                "canParticipate": true,
                "ownerPreview": false
              },
              "error": null
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
