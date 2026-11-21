package com.botem.domain.account.controller.docs

import com.botem.domain.account.dto.request.RotatePayoutAccountEncryptionRequest
import com.botem.domain.account.dto.response.PayoutAccountEncryptionRotationResponse
import com.botem.global.config.SwaggerConfig
import com.botem.global.response.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.parameters.RequestBody
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Admin Payout Account Encryption", description = "운영자 계좌 암호화 키 교체 API")
@SecurityRequirement(name = SwaggerConfig.ADMIN_API_KEY_AUTH)
interface AdminPayoutAccountEncryptionControllerDocs {

    @Operation(
        summary = "계좌번호 재암호화 배치 실행",
        description = """
            운영자가 이전 키로 암호화된 계좌번호를 현재 키로 재암호화합니다. X-Admin-Key 헤더가 필요합니다.
            한 번에 batchSize개까지만 처리하며, nextAfterAccountId가 null이 아닐 때 해당 값을 다음 요청의 afterAccountId로 보내면 중단 지점부터 이어서 처리합니다.
            이미 현재 키로 암호화된 계좌는 건너뛰므로 같은 요청을 다시 실행해도 안전합니다.
            계좌번호 원문과 암호문은 응답·로그에 포함하지 않습니다.
        """,
    )
    @RequestBody(
        content = [
            Content(
                examples = [
                    ExampleObject(
                        value = """
                            {
                              "afterAccountId": 1000,
                              "batchSize": 100
                            }
                        """,
                    ),
                ],
            ),
        ],
    )
    @ApiResponses(
        SwaggerApiResponse(
            responseCode = "200",
            description = "배치 처리 성공",
            content = [Content(examples = [ExampleObject(value = SUCCESS_EXAMPLE)])],
        ),
        SwaggerApiResponse(responseCode = "400", description = "커서 또는 배치 크기 입력 오류"),
        SwaggerApiResponse(responseCode = "401", description = "운영자 키 없음 또는 불일치"),
    )
    fun rotate(
        request: RotatePayoutAccountEncryptionRequest,
    ): ApiResponse<PayoutAccountEncryptionRotationResponse>

    companion object {
        private const val SUCCESS_EXAMPLE = """
            {
              "success": true,
              "data": {
                "processedCount": 100,
                "rotatedCount": 72,
                "nextAfterAccountId": 1100
              },
              "error": null
            }
        """
    }
}
