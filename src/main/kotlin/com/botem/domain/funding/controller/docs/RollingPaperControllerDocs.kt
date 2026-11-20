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
        summary = "생일 펀딩 롤링페이퍼 조회",
        description = """
            생일 펀딩 개설자가 팡(결과)을 연 뒤 도착한 롤링페이퍼 메시지를 조회합니다 (FUND-PG-010, FUND-PG-006(2) '롤링페이퍼 보기').
            개설자만 조회할 수 있고, 결과를 열기 전에는 FUNDING_058을 반환합니다.
            celebrantName은 '생일 축하해요 {이름}님!' 문구에 쓰는 개설자 이름(이름이 없으면 닉네임)이고, messageCount는 메시지 수입니다.
            messages는 취소된 보탬을 제외하고 보탠 순서대로 반환하며, 텍스트·편지지·스티커·사진·음성을 포함합니다.
            비로그인 참여자의 participantProfileImageUrl은 null이므로 기본 이미지는 프론트엔드에서 표시합니다.
            서프라이즈 받는 사람은 서프라이즈 공개 상세 API에서 같은 메시지 목록을 조회합니다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = [Content(examples = [ExampleObject(value = ROLLING_PAPER_EXAMPLE)])],
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
                    "voiceDurationSeconds": null
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
                    "voiceDurationSeconds": 12
                  }
                ]
              },
              "error": null
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
