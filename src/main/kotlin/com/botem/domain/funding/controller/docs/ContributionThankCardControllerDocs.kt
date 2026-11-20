package com.botem.domain.funding.controller.docs

import com.botem.domain.funding.dto.request.SendContributionThankCardRequest
import com.botem.domain.funding.dto.response.ContributionThankCardDetailResponse
import com.botem.domain.funding.dto.response.ContributionThankCardResponse
import com.botem.domain.funding.dto.response.SurpriseThankCardFriendsResponse
import com.botem.global.response.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.parameters.RequestBody
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Contribution Thank Card", description = "개설자 → 참여자 감사카드 API")
interface ContributionThankCardControllerDocs {

    @Operation(
        summary = "참여자 감사카드 보내기",
        description = """
            펀딩 개설자가 입금 확인이 완료된 보탬에 감사카드를 보냅니다.
            메시지는 공백 제거 후 Unicode 코드 포인트 기준 1~50자이며, 보탬 한 건에 한 번만 전송할 수 있습니다.
            paperColor는 편지지 색상으로 ORANGE·LIME·PINK·BLUE 중 하나이며, 생략하면 LIME입니다.
            같은 보탬에 다시 요청하면 최초 감사카드를 반환하고 메시지·색상·전송 시각은 변경하지 않습니다.
            응답의 shareUrl은 이미지 저장·공유하기·카카오톡 공유 화면 진입에 사용합니다.
        """,
    )
    @RequestBody(
        required = true,
        content = [Content(examples = [ExampleObject(value = SEND_REQUEST_EXAMPLE)])],
    )
    @ApiResponses(
        SwaggerApiResponse(
            responseCode = "200",
            description = "전송 성공 또는 기존 감사카드 반환",
            content = [Content(examples = [ExampleObject(value = SEND_RESPONSE_EXAMPLE)])],
        ),
        SwaggerApiResponse(
            responseCode = "400",
            description = "메시지 입력 오류",
            content = [Content(examples = [ExampleObject(value = INVALID_MESSAGE_EXAMPLE)])],
        ),
        SwaggerApiResponse(responseCode = "401", description = "인증 실패"),
        SwaggerApiResponse(
            responseCode = "403",
            description = "펀딩 개설자가 아님",
            content = [Content(examples = [ExampleObject(value = OWNER_ONLY_EXAMPLE)])],
        ),
        SwaggerApiResponse(
            responseCode = "404",
            description = "펀딩 또는 보탬 없음",
            content = [Content(examples = [ExampleObject(value = CONTRIBUTION_NOT_FOUND_EXAMPLE)])],
        ),
        SwaggerApiResponse(
            responseCode = "409",
            description = "입금 확인이 완료되지 않은 보탬",
            content = [Content(examples = [ExampleObject(value = CONFIRMED_ONLY_EXAMPLE)])],
        ),
    )
    fun sendThankCard(
        @Parameter(hidden = true) userId: Long,
        @Parameter(description = "펀딩 ID") fundingId: Long,
        @Parameter(description = "보탬 ID") contributionId: Long,
        request: SendContributionThankCardRequest,
    ): ApiResponse<ContributionThankCardResponse>

    @Operation(
        summary = "내가 받은 감사카드 조회",
        description = """
            로그인 참여자가 자신의 보탬으로 받은 감사카드를 조회합니다.
            다른 회원의 보탬이거나 감사카드가 없으면 동일하게 404를 반환합니다.
            감사카드에는 보탠 금액을 포함하지 않습니다.
            paperColor는 보낸 사람이 고른 편지지 색상입니다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = [Content(examples = [ExampleObject(value = DETAIL_RESPONSE_EXAMPLE)])],
        ),
        SwaggerApiResponse(responseCode = "401", description = "인증 실패"),
        SwaggerApiResponse(
            responseCode = "404",
            description = "본인이 받은 감사카드 없음",
            content = [Content(examples = [ExampleObject(value = THANK_CARD_NOT_FOUND_EXAMPLE)])],
        ),
    )
    fun getMyThankCard(
        @Parameter(hidden = true) userId: Long,
        @Parameter(description = "보탬 ID") contributionId: Long,
    ): ApiResponse<ContributionThankCardDetailResponse>

    @Operation(
        summary = "공유된 감사카드 조회",
        description = """
            로그인 없이 공유 링크의 키로 감사카드를 조회합니다.
            응답에는 보탠 금액이 포함되지 않으며 공유 키가 올바르지 않거나 카드가 없으면 404를 반환합니다.
            paperColor는 보낸 사람이 고른 편지지 색상입니다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = [Content(examples = [ExampleObject(value = DETAIL_RESPONSE_EXAMPLE)])],
        ),
        SwaggerApiResponse(
            responseCode = "404",
            description = "감사카드 없음",
            content = [Content(examples = [ExampleObject(value = THANK_CARD_NOT_FOUND_EXAMPLE)])],
        ),
    )
    fun getSharedThankCard(
        @Parameter(description = "감사카드 공유 키") shareKey: String,
    ): ApiResponse<ContributionThankCardDetailResponse>

    @Operation(
        summary = "서프라이즈 고마운 친구 목록 조회",
        description = """
            공개된 서프라이즈를 연 수신자가 참여자별 감사카드 발송 대상을 조회합니다.
            취소된 보탬은 제외하며, 참여 순서대로 보탬 ID·이름·메시지 구성·보탠 날짜·감사카드 발송 여부를 반환합니다.
            organizer가 true인 항목은 서프라이즈를 연 주최자의 보탬입니다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "조회 성공"),
        SwaggerApiResponse(responseCode = "401", description = "인증 실패"),
        SwaggerApiResponse(responseCode = "403", description = "서프라이즈 수신자가 아님"),
        SwaggerApiResponse(responseCode = "404", description = "펀딩 없음 또는 공개 전 서프라이즈"),
        SwaggerApiResponse(responseCode = "409", description = "서프라이즈를 열기 전"),
    )
    fun getSurpriseThankCardFriends(
        @Parameter(hidden = true) userId: Long,
        @Parameter(description = "서프라이즈 펀딩 ID") fundingId: Long,
    ): ApiResponse<SurpriseThankCardFriendsResponse>

    companion object {
        private const val SEND_REQUEST_EXAMPLE = """
            {
              "message": "덕분에 정말 행복한 생일이 됐어!",
              "paperColor": "PINK"
            }
        """
        private const val SEND_RESPONSE_EXAMPLE = """
            {
              "success": true,
              "data": {
                "thankCardId": 15,
                "contributionId": 31,
                "message": "덕분에 정말 행복한 생일이 됐어!",
                "paperColor": "PINK",
                "sentAt": "2026-11-19T20:00:00",
                "shareUrl": "https://botem.me/thank-cards/550e8400e29b41d4a716446655440000"
              },
              "error": null
            }
        """
        private const val DETAIL_RESPONSE_EXAMPLE = """
            {
              "success": true,
              "data": {
                "thankCardId": 15,
                "fundingId": 12,
                "contributionId": 31,
                "senderName": "지유",
                "recipientName": "하린",
                "productName": "소니 WH-1000XM5",
                "productImageUrl": "https://image.example.com/product.jpg",
                "message": "덕분에 정말 행복한 생일이 됐어!",
                "paperColor": "PINK",
                "sentAt": "2026-11-19T20:00:00",
                "shareUrl": "https://botem.me/thank-cards/550e8400e29b41d4a716446655440000"
              },
              "error": null
            }
        """
        private const val INVALID_MESSAGE_EXAMPLE = """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "COMMON_001",
                "message": "입력값이 올바르지 않습니다.",
                "detail": "message: 50자 이내로 입력해 주세요"
              }
            }
        """
        private const val OWNER_ONLY_EXAMPLE = """
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
        private const val CONTRIBUTION_NOT_FOUND_EXAMPLE = """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "FUNDING_023",
                "message": "참여 내역을 찾을 수 없습니다.",
                "detail": null
              }
            }
        """
        private const val CONFIRMED_ONLY_EXAMPLE = """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "FUNDING_049",
                "message": "입금이 확인된 보탬에만 감사 카드를 보낼 수 있습니다.",
                "detail": null
              }
            }
        """
        private const val THANK_CARD_NOT_FOUND_EXAMPLE = """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "FUNDING_050",
                "message": "감사 카드를 찾을 수 없습니다.",
                "detail": null
              }
            }
        """
    }
}
