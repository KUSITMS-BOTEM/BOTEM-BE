package com.botem.domain.user.controller.docs

import com.botem.domain.user.dto.request.UpdateMyProfileRequest
import com.botem.domain.user.dto.response.MyProfileResponse
import com.botem.global.response.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "User", description = "사용자 API")
interface UserControllerDocs {

    @Operation(
        summary = "내 프로필 조회",
        description = "로그인한 사용자의 닉네임, 프로필 이미지, 생일과 카카오 계정 연결 상태를 조회합니다.",
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
        description = "로그인한 사용자의 닉네임과 생일을 수정합니다. 전체 수정 값을 전달해야 합니다.",
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

    companion object {
        private const val PROFILE_RESPONSE_EXAMPLE = """
            {
              "success": true,
              "data": {
                "nickname": "지유",
                "profileImageUrl": "https://example.com/profile.jpg",
                "birthday": "2000-10-13",
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
    }
}
