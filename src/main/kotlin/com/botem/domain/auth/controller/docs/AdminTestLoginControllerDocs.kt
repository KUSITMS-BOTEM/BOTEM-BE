package com.botem.domain.auth.controller.docs

import com.botem.domain.auth.dto.request.AdminTestLoginRequest
import com.botem.domain.auth.dto.response.AccessTokenResponse
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

@Tag(name = "Admin Test Login", description = "운영자 전용 테스트 로그인 API")
@SecurityRequirement(name = SwaggerConfig.ADMIN_API_KEY_AUTH)
interface AdminTestLoginControllerDocs {

    @Operation(
        summary = "테스트 Access Token 발급",
        description = """
            X-Admin-Key가 일치하는 운영자만 기존 보탬 회원 이메일로 Access Token을 발급받습니다.
            ACTIVE 상태 회원이 정확히 한 명일 때만 발급하며, 카카오 OAuth 호출·회원 자동가입·친구 동기화·Refresh Token 쿠키 발급은 하지 않습니다.
            응답의 accessToken을 Swagger Authorize에 입력하면 해당 회원 권한으로 API를 테스트할 수 있습니다.
        """,
    )
    @RequestBody(
        content = [
            Content(
                examples = [
                    ExampleObject(value = """{ "email": "demo@botem.com" }"""),
                ],
            ),
        ],
    )
    @ApiResponses(
        SwaggerApiResponse(
            responseCode = "200",
            description = "Access Token 발급 성공",
            content = [Content(examples = [ExampleObject(value = SUCCESS_EXAMPLE)])],
        ),
        SwaggerApiResponse(responseCode = "400", description = "이메일 입력 형식 오류"),
        SwaggerApiResponse(responseCode = "401", description = "운영자 키 없음 또는 불일치"),
        SwaggerApiResponse(
            responseCode = "404",
            description = "활성 회원 없음 또는 이메일이 중복된 활성 회원",
            content = [Content(examples = [ExampleObject(value = USER_NOT_FOUND_EXAMPLE)])],
        ),
    )
    fun login(request: AdminTestLoginRequest): ApiResponse<AccessTokenResponse>

    companion object {
        private const val SUCCESS_EXAMPLE = """
            {
              "success": true,
              "data": {
                "accessToken": "eyJhbGciOiJIUzM4NCJ9..."
              },
              "error": null
            }
        """
        private const val USER_NOT_FOUND_EXAMPLE = """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "USER_001",
                "message": "사용자를 찾을 수 없습니다.",
                "detail": null
              }
            }
        """
    }
}
