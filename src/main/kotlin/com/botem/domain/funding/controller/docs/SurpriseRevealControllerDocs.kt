package com.botem.domain.funding.controller.docs

import com.botem.domain.funding.dto.request.SendSurpriseThankCardRequest
import com.botem.domain.funding.dto.response.SurpriseRevealDetailResponse
import com.botem.domain.funding.dto.response.SurpriseThankCardResponse
import com.botem.global.response.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.parameters.RequestBody
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Surprise Reveal", description = "서프라이즈 공개 API")
interface SurpriseRevealControllerDocs {

    @Operation(
        summary = "서프라이즈 공개 상세 조회",
        description = """
            공개 시각이 지난 서프라이즈의 선물 정보와 롤링페이퍼를 조회합니다.
            서프라이즈를 받는 로그인 사용자만 조회할 수 있으며, 공개 전에는 존재 여부를 노출하지 않습니다.
            비로그인 참여자의 participantProfileImageUrl은 null로 반환되므로 기본 이미지는 프론트엔드에서 표시합니다.
            이 API는 열람 상태를 변경하지 않습니다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = [Content(examples = [ExampleObject(value = DETAIL_RESPONSE_EXAMPLE)])],
        ),
        SwaggerApiResponse(
            responseCode = "400",
            description = "서프라이즈 펀딩이 아님",
            content = [Content(examples = [ExampleObject(value = SURPRISE_REQUIRED_EXAMPLE)])],
        ),
        SwaggerApiResponse(responseCode = "401", description = "인증 실패"),
        SwaggerApiResponse(
            responseCode = "403",
            description = "서프라이즈 수신자가 아님",
            content = [Content(examples = [ExampleObject(value = RECIPIENT_ONLY_EXAMPLE)])],
        ),
        SwaggerApiResponse(
            responseCode = "404",
            description = "펀딩이 없거나 아직 공개되지 않음",
            content = [
                Content(
                    examples = [
                        ExampleObject(name = "FUNDING_002", summary = "펀딩 없음", value = FUNDING_NOT_FOUND_EXAMPLE),
                        ExampleObject(
                            name = "FUNDING_016",
                            summary = "공개 전",
                            value = REVEAL_NOT_AVAILABLE_EXAMPLE,
                        ),
                    ],
                ),
            ],
        ),
        SwaggerApiResponse(
            responseCode = "409",
            description = "공개 상세를 조회할 수 없는 상태",
            content = [Content(examples = [ExampleObject(value = REVEAL_NOT_OPENABLE_EXAMPLE)])],
        ),
    )
    fun getSurpriseRevealDetail(
        @Parameter(hidden = true) userId: Long,
        @Parameter(description = "서프라이즈 펀딩 ID") fundingId: Long,
    ): ApiResponse<SurpriseRevealDetailResponse>

    @Operation(
        summary = "서프라이즈 열기",
        description = """
            공개 시각이 지난 서프라이즈를 열고 WAITING_REVEAL 상태를 REVEALED로 변경합니다.
            처음 연 시각을 openedAt에 저장하며, 이미 연 서프라이즈에 다시 요청하면 상태를 변경하지 않고 기존 상세를 반환합니다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(
            responseCode = "200",
            description = "열기 성공 또는 기존 공개 상세 반환",
            content = [Content(examples = [ExampleObject(value = OPEN_RESPONSE_EXAMPLE)])],
        ),
        SwaggerApiResponse(
            responseCode = "400",
            description = "서프라이즈 펀딩이 아님",
            content = [Content(examples = [ExampleObject(value = SURPRISE_REQUIRED_EXAMPLE)])],
        ),
        SwaggerApiResponse(responseCode = "401", description = "인증 실패"),
        SwaggerApiResponse(
            responseCode = "403",
            description = "서프라이즈 수신자가 아님",
            content = [Content(examples = [ExampleObject(value = RECIPIENT_ONLY_EXAMPLE)])],
        ),
        SwaggerApiResponse(
            responseCode = "404",
            description = "펀딩이 없거나 아직 공개되지 않음",
            content = [
                Content(
                    examples = [
                        ExampleObject(name = "FUNDING_002", summary = "펀딩 없음", value = FUNDING_NOT_FOUND_EXAMPLE),
                        ExampleObject(
                            name = "FUNDING_016",
                            summary = "공개 전",
                            value = REVEAL_NOT_AVAILABLE_EXAMPLE,
                        ),
                    ],
                ),
            ],
        ),
        SwaggerApiResponse(
            responseCode = "409",
            description = "서프라이즈를 열 수 없는 상태",
            content = [Content(examples = [ExampleObject(value = REVEAL_NOT_OPENABLE_EXAMPLE)])],
        ),
    )
    fun openSurprise(
        @Parameter(hidden = true) userId: Long,
        @Parameter(description = "서프라이즈 펀딩 ID") fundingId: Long,
    ): ApiResponse<SurpriseRevealDetailResponse>

    @Operation(
        summary = "서프라이즈 감사 카드 전송",
        description = """
            열린 서프라이즈의 주최자와 참여자에게 전달할 감사 메시지를 저장합니다.
            메시지는 앞뒤 공백을 제거한 뒤 최대 1,000자까지 저장합니다.
            이미 감사 카드를 보낸 펀딩에 다시 요청하면 최초에 저장한 카드를 반환하며 내용을 변경하지 않습니다.
            감사 카드 전송 후 홈 공개 카드에서는 제외되지만, 펀딩의 최종 완료는 별도의 선물 인증에서 처리합니다.
        """,
    )
    @RequestBody(
        content = [Content(examples = [ExampleObject(value = THANK_CARD_REQUEST_EXAMPLE)])],
    )
    @ApiResponses(
        SwaggerApiResponse(
            responseCode = "200",
            description = "감사 카드 전송 또는 기존 감사 카드 반환",
            content = [Content(examples = [ExampleObject(value = THANK_CARD_RESPONSE_EXAMPLE)])],
        ),
        SwaggerApiResponse(
            responseCode = "400",
            description = "서프라이즈 펀딩이 아니거나 메시지가 올바르지 않음",
            content = [
                Content(
                    examples = [
                        ExampleObject(name = "FUNDING_014", summary = "서프라이즈 아님", value = SURPRISE_REQUIRED_EXAMPLE),
                        ExampleObject(name = "COMMON_001", summary = "메시지 오류", value = INVALID_INPUT_EXAMPLE),
                    ],
                ),
            ],
        ),
        SwaggerApiResponse(responseCode = "401", description = "인증 실패"),
        SwaggerApiResponse(
            responseCode = "403",
            description = "서프라이즈 수신자가 아님",
            content = [Content(examples = [ExampleObject(value = RECIPIENT_ONLY_EXAMPLE)])],
        ),
        SwaggerApiResponse(
            responseCode = "404",
            description = "펀딩이 없거나 아직 공개되지 않음",
            content = [
                Content(
                    examples = [
                        ExampleObject(name = "FUNDING_002", summary = "펀딩 없음", value = FUNDING_NOT_FOUND_EXAMPLE),
                        ExampleObject(
                            name = "FUNDING_016",
                            summary = "공개 전",
                            value = REVEAL_NOT_AVAILABLE_EXAMPLE,
                        ),
                    ],
                ),
            ],
        ),
        SwaggerApiResponse(
            responseCode = "409",
            description = "서프라이즈를 열지 않았거나 감사 카드를 보낼 수 없는 상태",
            content = [
                Content(
                    examples = [
                        ExampleObject(
                            name = "FUNDING_017",
                            summary = "처리 불가 상태",
                            value = REVEAL_NOT_OPENABLE_EXAMPLE,
                        ),
                        ExampleObject(name = "FUNDING_018", summary = "열기 전", value = NOT_OPENED_EXAMPLE),
                    ],
                ),
            ],
        ),
    )
    fun sendThankCard(
        @Parameter(hidden = true) userId: Long,
        @Parameter(description = "서프라이즈 펀딩 ID") fundingId: Long,
        request: SendSurpriseThankCardRequest,
    ): ApiResponse<SurpriseThankCardResponse>

    companion object {
        private const val DETAIL_RESPONSE_EXAMPLE = """
            {
              "success": true,
              "data": {
                "fundingId": 31,
                "recipientName": "지유",
                "organizerName": "민지",
                "productName": "다이슨 에어랩",
                "productImageUrl": "https://image.example.com/product.jpg",
                "confirmedAmount": 429000,
                "participantCount": 8,
                "messageCount": 2,
                "revealAt": "2026-11-15T00:00:00",
                "openedAt": null,
                "thankCardSent": false,
                "messages": [
                  {
                    "messageId": 101,
                    "participantName": "하은",
                    "participantProfileImageUrl": "https://image.example.com/profile.jpg",
                    "content": "생일 진짜 축하해!",
                    "paperTemplateCode": "BLUE_DOT_GRID",
                    "decorationCode": null,
                    "photoUrl": null,
                    "voiceUrl": null,
                    "voiceDurationSeconds": null
                  },
                  {
                    "messageId": 102,
                    "participantName": "익명의 친구",
                    "participantProfileImageUrl": null,
                    "content": "항상 고마워 친구야",
                    "paperTemplateCode": "BLACK_CONFETTI",
                    "decorationCode": null,
                    "photoUrl": null,
                    "voiceUrl": "https://cdn.example.com/voice/102.m4a",
                    "voiceDurationSeconds": 12
                  }
                ]
              },
              "error": null
            }
        """
        private const val OPEN_RESPONSE_EXAMPLE = """
            {
              "success": true,
              "data": {
                "fundingId": 31,
                "recipientName": "지유",
                "organizerName": "민지",
                "productName": "다이슨 에어랩",
                "productImageUrl": "https://image.example.com/product.jpg",
                "confirmedAmount": 429000,
                "participantCount": 8,
                "messageCount": 0,
                "revealAt": "2026-11-15T00:00:00",
                "openedAt": "2026-11-15T09:41:00",
                "thankCardSent": false,
                "messages": []
              },
              "error": null
            }
        """
        private const val THANK_CARD_REQUEST_EXAMPLE = """
            {
              "message": "다들 언제 이렇게 준비했어 ㅠㅠ 정말 고마워!"
            }
        """
        private const val THANK_CARD_RESPONSE_EXAMPLE = """
            {
              "success": true,
              "data": {
                "thankCardId": 7,
                "fundingId": 31,
                "message": "다들 언제 이렇게 준비했어 ㅠㅠ 정말 고마워!",
                "sentAt": "2026-11-15T09:45:00"
              },
              "error": null
            }
        """
        private const val FUNDING_NOT_FOUND_EXAMPLE = """
            {"success":false,"data":null,"error":{"code":"FUNDING_002","message":"펀딩을 찾을 수 없습니다.","detail":null}}
        """
        private const val SURPRISE_REQUIRED_EXAMPLE = """
            {"success":false,"data":null,"error":{"code":"FUNDING_014","message":"서프라이즈 펀딩에서만 사용할 수 있습니다.","detail":null}}
        """
        private const val RECIPIENT_ONLY_EXAMPLE = """
            {"success":false,"data":null,"error":{"code":"FUNDING_015","message":"서프라이즈를 받는 사용자만 접근할 수 있습니다.","detail":null}}
        """
        private const val REVEAL_NOT_AVAILABLE_EXAMPLE = """
            {"success":false,"data":null,"error":{"code":"FUNDING_016","message":"공개된 서프라이즈를 찾을 수 없습니다.","detail":null}}
        """
        private const val REVEAL_NOT_OPENABLE_EXAMPLE = """
            {"success":false,"data":null,"error":{"code":"FUNDING_017","message":"현재 상태에서는 서프라이즈를 열 수 없습니다.","detail":null}}
        """
        private const val NOT_OPENED_EXAMPLE = """
            {"success":false,"data":null,"error":{"code":"FUNDING_018","message":"서프라이즈를 연 후 감사 카드를 보낼 수 있습니다.","detail":null}}
        """
        private const val INVALID_INPUT_EXAMPLE = """
            {"success":false,"data":null,"error":{"code":"COMMON_001","message":"입력값이 올바르지 않습니다.","detail":null}}
        """
    }
}
