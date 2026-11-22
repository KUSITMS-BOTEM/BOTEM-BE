package com.botem.domain.funding.controller.docs

import com.botem.domain.funding.dto.response.SurpriseRevealDetailResponse
import com.botem.global.response.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
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
            targetAmount는 목표 금액, confirmedPercentage는 확정 금액 기준 달성률(홈 카드·펀딩 상세와 같은 기준, 소수점 버림, 초과 달성 시 100 초과 가능),
            deadline은 보태기 마감 시각입니다.
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
                "targetAmount": 429000,
                "confirmedPercentage": 100,
                "deadline": "2026-11-14T23:59:59.999999",
                "participantCount": 8,
                "messageCount": 2,
                "revealAt": "2026-11-15T00:00:00",
                "openedAt": null,
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
                    "voiceDurationSeconds": null,
                    "voiceTranscript": null
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
                    "voiceDurationSeconds": 12,
                    "voiceTranscript": "생일 진짜 축하해!"
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
                "targetAmount": 429000,
                "confirmedPercentage": 100,
                "deadline": "2026-11-14T23:59:59.999999",
                "participantCount": 8,
                "messageCount": 0,
                "revealAt": "2026-11-15T00:00:00",
                "openedAt": "2026-11-15T09:41:00",
                "messages": []
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
    }
}
