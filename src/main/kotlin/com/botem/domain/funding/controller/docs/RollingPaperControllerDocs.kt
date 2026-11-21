package com.botem.domain.funding.controller.docs

import com.botem.domain.funding.dto.response.RollingPaperResponse
import com.botem.global.response.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Rolling Paper", description = "롤링페이퍼 API")
interface RollingPaperControllerDocs {

    @Operation(
        summary = "지난 펀딩 롤링페이퍼 조회",
        description = """
            개설자·받는 사람·취소되지 않은 참여자만 지난 펀딩의 롤링페이퍼를 조회합니다.
            생일 펀딩은 개설자가 팡(결과)을 연 뒤, 서프라이즈 펀딩은 선물 준비가 완료되고 공개 시각이 지난 뒤 조회할 수 있습니다.
            celebrantName은 생일 펀딩의 개설자, 서프라이즈 펀딩의 받는 사람 이름(이름이 없으면 닉네임)이고, messageCount는 메시지 수입니다.
            messages는 취소된 보탬을 제외하고 보탠 순서대로 반환하며, 텍스트·편지지·스티커·사진·음성을 포함합니다.
            voiceTranscript는 음성 아래에 보여줄 자막이며, 음성이 없거나 자막을 보내지 않았으면 null입니다.
            비로그인 참여자의 participantProfileImageUrl은 null이므로 기본 이미지는 프론트엔드에서 표시합니다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = [Content(examples = [ExampleObject(value = ROLLING_PAPER_EXAMPLE)])],
        ),
        SwaggerApiResponse(responseCode = "401", description = "인증 실패"),
        SwaggerApiResponse(
            responseCode = "403",
            description = "조회 권한 없음 또는 공개 전 서프라이즈",
            content = [
                Content(
                    examples = [
                        ExampleObject(name = "조회 권한 없음", value = ROLLING_PAPER_VIEWER_ONLY_EXAMPLE),
                        ExampleObject(name = "공개 전 서프라이즈", value = SURPRISE_NOT_REVEALED_EXAMPLE),
                    ],
                ),
            ],
        ),
        SwaggerApiResponse(
            responseCode = "404",
            description = "펀딩 없음 또는 취소된 펀딩",
            content = [Content(examples = [ExampleObject(value = FUNDING_NOT_FOUND_EXAMPLE)])],
        ),
        SwaggerApiResponse(
            responseCode = "409",
            description = "결과를 열기 전",
            content = [Content(examples = [ExampleObject(value = ROLLING_PAPER_NOT_OPENED_EXAMPLE)])],
        ),
    )
    fun getRollingPaper(
        @Parameter(hidden = true) userId: Long,
        @Parameter(description = "펀딩 ID") fundingId: Long,
    ): ApiResponse<RollingPaperResponse>

    companion object {
        private const val ROLLING_PAPER_EXAMPLE = """
            {
              "success": true,
              "data": {
                "fundingId": 12,
                "celebrantName": "지유",
                "messageCount": 2,
                "messages": [
                  {
                    "messageId": 41,
                    "participantName": "서준",
                    "participantProfileImageUrl": "https://image.example.com/profile/seojun.jpg",
                    "content": "생일 진짜 축하해!",
                    "paperTemplateCode": "LIME_DOT_GRID",
                    "decorationCode": "CAKE",
                    "photoUrl": "https://image.example.com/rolling-paper/photo.jpg",
                    "voiceUrl": null,
                    "voiceDurationSeconds": null,
                    "voiceTranscript": null
                  },
                  {
                    "messageId": 42,
                    "participantName": "하린",
                    "participantProfileImageUrl": null,
                    "content": null,
                    "paperTemplateCode": "BLUE_DOT_GRID",
                    "decorationCode": null,
                    "photoUrl": null,
                    "voiceUrl": "https://image.example.com/rolling-paper/voice.m4a",
                    "voiceDurationSeconds": 12,
                    "voiceTranscript": "생일 진짜 축하해!"
                  }
                ]
              },
              "error": null
            }
        """
        private const val ROLLING_PAPER_VIEWER_ONLY_EXAMPLE = """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "FUNDING_060",
                "message": "펀딩 개설자, 받는 사람 또는 참여자만 롤링페이퍼를 볼 수 있습니다.",
                "detail": null
              }
            }
        """
        private const val SURPRISE_NOT_REVEALED_EXAMPLE = """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "FUNDING_046",
                "message": "아직 공개되지 않은 서프라이즈입니다.",
                "detail": null
              }
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
        private const val ROLLING_PAPER_NOT_OPENED_EXAMPLE = """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "FUNDING_058",
                "message": "펀딩 결과를 연 뒤 롤링페이퍼를 볼 수 있습니다.",
                "detail": null
              }
            }
        """
    }
}
