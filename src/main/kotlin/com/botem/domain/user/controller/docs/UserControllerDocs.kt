package com.botem.domain.user.controller.docs

import com.botem.domain.user.dto.request.RegisterOnboardingRequest
import com.botem.domain.user.dto.request.UpdateMyProfileRequest
import com.botem.domain.user.dto.response.MyProfileResponse
import com.botem.global.response.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.parameters.RequestBody
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity

@Tag(name = "User", description = "사용자 API")
interface UserControllerDocs {

    @Operation(
        summary = "내 프로필 조회",
        description = "로그인한 사용자의 닉네임, 프로필 이미지, 생일, 위시 프로필 링크와 카카오 계정 연결 상태를 조회합니다.",
    )
    @ApiResponses(
        SwaggerApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = [Content(examples = [ExampleObject(value = PROFILE_RESPONSE_EXAMPLE)])],
        ),
        SwaggerApiResponse(
            responseCode = "401",
            description = "인증 실패",
        ),
        SwaggerApiResponse(
            responseCode = "404",
            description = "사용자 없음",
            content = [Content(examples = [ExampleObject(value = USER_NOT_FOUND_EXAMPLE)])],
        ),
    )
    fun getMyProfile(
        @Parameter(hidden = true) userId: Long,
    ): ApiResponse<MyProfileResponse>

    @Operation(
        summary = "내 프로필 수정",
        description = "로그인한 사용자의 닉네임과 생일을 수정합니다. 전체 수정 값을 전달해야 합니다. 생일은 필수이며 연도 없이 `MM-dd` 형식으로 전달합니다.",
    )
    @ApiResponses(
        SwaggerApiResponse(
            responseCode = "200",
            description = "수정 성공",
            content = [Content(examples = [ExampleObject(value = PROFILE_RESPONSE_EXAMPLE)])],
        ),
        SwaggerApiResponse(
            responseCode = "400",
            description = "요청 값 오류",
            content = [Content(examples = [ExampleObject(value = INVALID_INPUT_EXAMPLE)])],
        ),
        SwaggerApiResponse(
            responseCode = "401",
            description = "인증 실패",
        ),
        SwaggerApiResponse(
            responseCode = "404",
            description = "사용자 없음",
            content = [Content(examples = [ExampleObject(value = USER_NOT_FOUND_EXAMPLE)])],
        ),
    )
    fun updateMyProfile(
        @Parameter(hidden = true) userId: Long,
        request: UpdateMyProfileRequest,
    ): ApiResponse<MyProfileResponse>

    @Operation(
        summary = "온보딩 정보 등록",
        description = """
            회원가입 온보딩의 마지막 단계("시작하기")에서 이름, 생일, 받을 계좌를 한 번에 등록합니다.
            생일은 필수이며 연도 없이 `MM-dd` 형식으로 전달합니다.
            계좌번호는 형식만 검증하며, 실제 계좌 존재 여부는 확인하지 않습니다.
            모든 정보가 함께 저장되며, 하나라도 실패하면 아무것도 저장되지 않습니다.
            받을 계좌가 이미 있는 회원(온보딩 완료)은 요청할 수 없습니다.
        """,
    )
    @RequestBody(
        content = [
            Content(
                examples = [
                    ExampleObject(
                        value = """{ "name": "이정민", "birthday": "10-13", "bank": "TOSS_BANK", "accountNumber": "100159014821" }""",
                    ),
                ],
            ),
        ],
    )
    @ApiResponses(
        SwaggerApiResponse(
            responseCode = "200",
            description = "등록 성공",
            content = [Content(examples = [ExampleObject(value = SUCCESS_EXAMPLE)])],
        ),
        SwaggerApiResponse(
            responseCode = "400",
            description = "요청 값 오류 또는 계좌번호 형식 오류",
            content = [
                Content(
                    examples = [
                        ExampleObject(name = "COMMON_001", summary = "이름 누락 등 요청 값 오류", value = INVALID_INPUT_EXAMPLE),
                        ExampleObject(
                            name = "PAYOUT_ACCOUNT_004",
                            summary = "계좌번호 형식 오류",
                            value = INVALID_ACCOUNT_NUMBER_EXAMPLE,
                        ),
                    ],
                ),
            ],
        ),
        SwaggerApiResponse(
            responseCode = "401",
            description = "인증 실패",
        ),
        SwaggerApiResponse(
            responseCode = "404",
            description = "사용자 없음",
            content = [Content(examples = [ExampleObject(value = USER_NOT_FOUND_EXAMPLE)])],
        ),
        SwaggerApiResponse(
            responseCode = "409",
            description = "이미 온보딩을 완료한 사용자",
            content = [Content(examples = [ExampleObject(value = ONBOARDING_ALREADY_COMPLETED_EXAMPLE)])],
        ),
    )
    fun completeOnboarding(
        @Parameter(hidden = true) userId: Long,
        request: RegisterOnboardingRequest,
    ): ApiResponse<Nothing>

    @Operation(
        summary = "회원 탈퇴",
        description = """
            로그인한 사용자를 탈퇴 처리합니다.
            진행 중인 일반 펀딩 또는 서프라이즈 펀딩이 있으면 탈퇴할 수 없습니다.
            탈퇴하면 Refresh 토큰과 받을 계좌를 삭제하고 개인정보를 익명화합니다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(
            responseCode = "200",
            description = "탈퇴 성공",
            content = [Content(examples = [ExampleObject(value = SUCCESS_EXAMPLE)])],
        ),
        SwaggerApiResponse(responseCode = "401", description = "인증 실패"),
        SwaggerApiResponse(
            responseCode = "404",
            description = "사용자 없음",
            content = [Content(examples = [ExampleObject(value = USER_NOT_FOUND_EXAMPLE)])],
        ),
        SwaggerApiResponse(
            responseCode = "409",
            description = "진행 중인 펀딩 존재",
            content = [Content(examples = [ExampleObject(value = ACTIVE_FUNDING_EXAMPLE)])],
        ),
        SwaggerApiResponse(
            responseCode = "503",
            description = "펀딩 확인 기능 미연결",
            content = [Content(examples = [ExampleObject(value = WITHDRAWAL_CHECK_UNAVAILABLE_EXAMPLE)])],
        ),
    )
    fun withdraw(
        @Parameter(hidden = true) userId: Long,
    ): ResponseEntity<ApiResponse<Nothing>>

    companion object {
        private const val SUCCESS_EXAMPLE =
            """{ "success": true, "data": null, "error": null }"""

        private const val INVALID_ACCOUNT_NUMBER_EXAMPLE = """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "PAYOUT_ACCOUNT_004",
                "message": "계좌번호 형식이 올바르지 않습니다.",
                "detail": null
              }
            }
        """

        private const val ONBOARDING_ALREADY_COMPLETED_EXAMPLE = """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "USER_004",
                "message": "이미 온보딩을 완료한 사용자입니다.",
                "detail": null
              }
            }
        """

        private const val PROFILE_RESPONSE_EXAMPLE = """
            {
              "success": true,
              "data": {
                "nickname": "지유",
                "profileImageUrl": "https://example.com/profile.jpg",
                "birthday": "10-13",
                "wishProfileUrl": "https://botem.site/@8f0c6d779fae47f3a979302f2782d20f",
                "kakaoConnected": true
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
                "detail": "nickname: 공백일 수 없습니다"
              }
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

        private const val ACTIVE_FUNDING_EXAMPLE = """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "USER_002",
                "message": "진행 중인 펀딩이 끝난 뒤 탈퇴할 수 있어요.",
                "detail": null
              }
            }
        """

        private const val WITHDRAWAL_CHECK_UNAVAILABLE_EXAMPLE = """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "USER_003",
                "message": "현재 회원 탈퇴 가능 여부를 확인할 수 없습니다.",
                "detail": null
              }
            }
        """
    }
}
