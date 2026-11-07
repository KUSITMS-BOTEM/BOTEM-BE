package com.botem.domain.auth.controller.docs

import com.botem.domain.auth.dto.request.KakaoLoginRequest
import com.botem.domain.auth.dto.response.AccessTokenResponse
import com.botem.domain.auth.dto.response.KakaoLoginResponse
import com.botem.domain.auth.dto.response.KakaoLoginUriResponse
import com.botem.global.response.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.parameters.RequestBody
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirements
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

@Tag(name = "Auth", description = "카카오 로그인, 토큰 재발급 및 로그아웃 API")
interface AuthControllerDocs {

    @Operation(
        summary = "카카오 로그인 URI 조회",
        description = """
            카카오 로그인 페이지 주소를 반환합니다.
            클라이언트는 이 주소로 이동시키고, 로그인이 끝나면 카카오가 redirect URI로 `code`를 전달합니다.
            `redirectUri`는 서버에 등록된 허용 목록에 있는 값만 사용할 수 있고, 생략하면 서버 기본값을 사용합니다.
            로그인 API에도 같은 `redirectUri`를 보내야 합니다.
        """,
    )
    @SecurityRequirements
    @ApiResponses(
        SwaggerApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = [
                Content(
                    examples = [
                        ExampleObject(
                            value = """
                                {
                                  "success": true,
                                  "data": {
                                    "loginUri": "https://kauth.kakao.com/oauth/authorize?client_id={REST_API_KEY}&redirect_uri={REDIRECT_URI}&response_type=code"
                                  },
                                  "error": null
                                }
                            """,
                        ),
                    ],
                ),
            ],
        ),
        SwaggerApiResponse(
            responseCode = "400",
            description = "허용되지 않은 redirect URI",
            content = [Content(examples = [ExampleObject(name = "AUTH_005", value = INVALID_REDIRECT_URI_EXAMPLE)])],
        ),
    )
    fun getKakaoLoginUri(
        @Parameter(description = "카카오 로그인 후 이동할 redirect URI", example = "https://botem.site/auth/callback")
        redirectUri: String?,
    ): ApiResponse<KakaoLoginUriResponse>

    @Operation(
        summary = "카카오 로그인",
        description = """
            카카오 인가 코드(`code`)로 로그인합니다.
            Access 토큰은 응답 본문으로, Refresh 토큰은 HttpOnly 쿠키로 발급합니다.
            브라우저가 쿠키를 저장하도록 요청에 `credentials: 'include'`를 설정해야 합니다.
            `redirectUri`는 로그인 URI 조회 때 보낸 값과 같아야 하며, 생략하면 서버 기본값을 사용합니다.
            처음 로그인한 사용자는 자동으로 회원가입됩니다.
            온보딩(받을 계좌 등록)을 마치지 않은 회원이면 `isNewUser`가 `true`입니다.
            클라이언트는 `isNewUser`가 `true`이면 온보딩 화면으로, `false`이면 메인 화면으로 이동합니다.
            인가 코드는 한 번만 사용할 수 있고 발급 후 몇 분 안에 만료됩니다.
            Access 토큰 유효기간은 60분, Refresh 토큰 유효기간은 7일입니다.
        """,
    )
    @SecurityRequirements
    @RequestBody(
        content = [
            Content(
                examples = [
                    ExampleObject(
                        value = """{ "code": "카카오에서 전달받은 인가 코드", "redirectUri": "https://botem.site/auth/callback" }""",
                    ),
                ],
            ),
        ],
    )
    @ApiResponses(
        SwaggerApiResponse(
            responseCode = "200",
            description = "로그인 성공",
            content = [Content(examples = [ExampleObject(value = LOGIN_RESPONSE_EXAMPLE)])],
        ),
        SwaggerApiResponse(
            responseCode = "400",
            description = "요청 값 오류 또는 카카오 로그인 실패",
            content = [
                Content(
                    examples = [
                        ExampleObject(name = "COMMON_001", summary = "code 누락", value = INVALID_INPUT_EXAMPLE),
                        ExampleObject(
                            name = "AUTH_004",
                            summary = "유효하지 않거나 만료된 인가 코드, 닉네임 미동의",
                            value = """
                                {
                                  "success": false,
                                  "data": null,
                                  "error": { "code": "AUTH_004", "message": "카카오 로그인에 실패했습니다.", "detail": null }
                                }
                            """,
                        ),
                        ExampleObject(
                            name = "AUTH_005",
                            summary = "허용되지 않은 redirect URI",
                            value = INVALID_REDIRECT_URI_EXAMPLE,
                        ),
                    ],
                ),
            ],
        ),
    )
    fun kakaoLogin(request: KakaoLoginRequest): ResponseEntity<ApiResponse<KakaoLoginResponse>>

    @Operation(
        summary = "토큰 재발급",
        description = """
            `refreshToken` 쿠키로 Access 토큰과 Refresh 토큰을 함께 재발급합니다.
            새 Access 토큰은 응답 본문으로, 새 Refresh 토큰은 쿠키로 전달하며 기존 Refresh 토큰은 더 이상 사용할 수 없습니다.
            요청에 `credentials: 'include'`를 설정해야 합니다.
            API 요청이 `AUTH_003`(만료된 토큰)으로 실패하면 이 API를 호출합니다.
        """,
    )
    @SecurityRequirements
    @ApiResponses(
        SwaggerApiResponse(
            responseCode = "200",
            description = "재발급 성공",
            content = [Content(examples = [ExampleObject(value = TOKEN_RESPONSE_EXAMPLE)])],
        ),
        SwaggerApiResponse(
            responseCode = "401",
            description = "유효하지 않거나 만료된 Refresh 토큰",
            content = [
                Content(
                    examples = [
                        ExampleObject(
                            name = "AUTH_002",
                            summary = "쿠키가 없거나, 위조됐거나 이미 재발급에 사용된 토큰",
                            value = """
                                {
                                  "success": false,
                                  "data": null,
                                  "error": { "code": "AUTH_002", "message": "유효하지 않은 토큰입니다.", "detail": null }
                                }
                            """,
                        ),
                        ExampleObject(
                            name = "AUTH_003",
                            summary = "만료된 Refresh 토큰, 다시 로그인 필요",
                            value = """
                                {
                                  "success": false,
                                  "data": null,
                                  "error": { "code": "AUTH_003", "message": "만료된 토큰입니다.", "detail": null }
                                }
                            """,
                        ),
                    ],
                ),
            ],
        ),
    )
    fun reissue(@Parameter(hidden = true) refreshToken: String?): ResponseEntity<ApiResponse<AccessTokenResponse>>

    @Operation(
        summary = "로그아웃",
        description = """
            `refreshToken` 쿠키와 서버에 저장된 Refresh 토큰을 삭제합니다.
            Access 토큰이 만료된 상태에서도 호출할 수 있고, 쿠키가 없거나 유효하지 않아도 성공으로 응답합니다.
            요청에 `credentials: 'include'`를 설정해야 하며, 클라이언트는 보관 중인 Access 토큰을 폐기해야 합니다.
        """,
    )
    @SecurityRequirements
    @ApiResponses(
        SwaggerApiResponse(
            responseCode = "200",
            description = "로그아웃 성공",
            content = [
                Content(
                    examples = [
                        ExampleObject(value = """{ "success": true, "data": null, "error": null }"""),
                    ],
                ),
            ],
        ),
    )
    fun logout(@Parameter(hidden = true) refreshToken: String?): ResponseEntity<ApiResponse<Nothing>>

    companion object {
        private const val LOGIN_RESPONSE_EXAMPLE = """
            {
              "success": true,
              "data": {
                "accessToken": "eyJhbGciOiJIUzM4NCJ9...",
                "isNewUser": true
              },
              "error": null
            }
        """

        private const val TOKEN_RESPONSE_EXAMPLE = """
            {
              "success": true,
              "data": {
                "accessToken": "eyJhbGciOiJIUzM4NCJ9..."
              },
              "error": null
            }
        """

        private const val INVALID_REDIRECT_URI_EXAMPLE = """
            {
              "success": false,
              "data": null,
              "error": { "code": "AUTH_005", "message": "허용되지 않은 redirect URI입니다.", "detail": null }
            }
        """

        private const val INVALID_INPUT_EXAMPLE = """
            {
              "success": false,
              "data": null,
              "error": { "code": "COMMON_001", "message": "입력값이 올바르지 않습니다.", "detail": "code: 공백일 수 없습니다" }
            }
        """
    }
}
