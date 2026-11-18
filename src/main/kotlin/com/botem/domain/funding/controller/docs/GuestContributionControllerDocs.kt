package com.botem.domain.funding.controller.docs

import com.botem.domain.funding.dto.request.AccessGuestContributionRequest
import com.botem.domain.funding.dto.request.UpdateGuestContributionRequest
import com.botem.domain.funding.dto.response.GuestContributionResponse
import com.botem.domain.funding.dto.response.UpdatedGuestContributionResponse
import com.botem.global.response.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.parameters.RequestBody
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Guest Contribution", description = "비회원 보탬 조회·수정·삭제 API")
interface GuestContributionControllerDocs {

    @Operation(
        summary = "비회원 보탬 조회 및 접근 토큰 발급",
        description = """
            참여할 때 사용한 이름과 숫자 4자리 비밀번호로 비회원 본인의 보탬을 조회합니다.
            이름이 없거나 비밀번호가 다른 경우 모두 FUNDING_025를 반환해 이름의 존재 여부를 노출하지 않습니다.
            같은 이름으로 5회 연속 실패하면 10분 동안 FUNDING_029를 반환하며, 성공하면 실패 횟수를 초기화합니다.
            응답의 단기 접근 토큰은 로그인 JWT가 아니며 수정·삭제 요청의 X-Guest-Contribution-Token 헤더에서만 사용합니다.
        """,
    )
    @RequestBody(
        required = true,
        content = [Content(examples = [ExampleObject(value = ACCESS_REQUEST_EXAMPLE)])],
    )
    @ApiResponses(
        SwaggerApiResponse(
            responseCode = "200",
            description = "조회 및 접근 토큰 발급 성공",
            content = [Content(examples = [ExampleObject(value = ACCESS_RESPONSE_EXAMPLE)])],
        ),
        SwaggerApiResponse(responseCode = "400", description = "이름 또는 비밀번호 형식 오류"),
        SwaggerApiResponse(
            responseCode = "403",
            description = "이름 또는 비밀번호 불일치",
            content = [Content(examples = [ExampleObject(value = PASSWORD_MISMATCH_EXAMPLE)])],
        ),
        SwaggerApiResponse(
            responseCode = "404",
            description = "펀딩 없음 또는 취소된 펀딩",
            content = [Content(examples = [ExampleObject(value = FUNDING_NOT_FOUND_EXAMPLE)])],
        ),
        SwaggerApiResponse(
            responseCode = "429",
            description = "비밀번호 연속 실패로 잠김",
            content = [Content(examples = [ExampleObject(value = PASSWORD_LOCKED_EXAMPLE)])],
        ),
    )
    fun access(
        @Parameter(description = "펀딩 ID") fundingId: Long,
        request: AccessGuestContributionRequest,
    ): ApiResponse<GuestContributionResponse>

    @Operation(
        summary = "비회원 보탬 수정",
        description = """
            접근 토큰으로 참여자 이름, 축하 메시지와 편지지를 수정합니다.
            토큰에 기록된 펀딩과 요청 경로가 다르거나 다른 보탬에 사용하면 FUNDING_033을 반환합니다.
            이름을 바꿀 때 같은 펀딩의 다른 비회원 보탬이 사용 중인 이름은 선택할 수 없습니다.
            보탠 금액, 입금 상태와 비밀번호는 변경하지 않습니다.
        """,
    )
    @RequestBody(
        required = true,
        content = [Content(examples = [ExampleObject(value = UPDATE_REQUEST_EXAMPLE)])],
    )
    @ApiResponses(
        SwaggerApiResponse(
            responseCode = "200",
            description = "수정 성공",
            content = [Content(examples = [ExampleObject(value = UPDATE_RESPONSE_EXAMPLE)])],
        ),
        SwaggerApiResponse(responseCode = "400", description = "수정 입력값 오류"),
        SwaggerApiResponse(
            responseCode = "401",
            description = "접근 토큰 누락·만료·위조 또는 다른 펀딩의 토큰",
            content = [Content(examples = [ExampleObject(value = TOKEN_INVALID_EXAMPLE)])],
        ),
        SwaggerApiResponse(
            responseCode = "404",
            description = "참여 내역 없음",
            content = [Content(examples = [ExampleObject(value = CONTRIBUTION_NOT_FOUND_EXAMPLE)])],
        ),
        SwaggerApiResponse(
            responseCode = "409",
            description = "다른 비회원 보탬이 사용 중인 이름",
            content = [Content(examples = [ExampleObject(value = NAME_DUPLICATED_EXAMPLE)])],
        ),
    )
    fun update(
        @Parameter(description = "펀딩 ID") fundingId: Long,
        @Parameter(description = "비회원 보탬 접근 토큰", required = true) accessToken: String?,
        request: UpdateGuestContributionRequest,
    ): ApiResponse<UpdatedGuestContributionResponse>

    @Operation(
        summary = "비회원 보탬 삭제",
        description = """
            접근 토큰으로 비회원 보탬을 CANCELLED 상태로 변경합니다.
            AWAITING_TRANSFER, MESSAGE_ONLY, REJECTED 상태만 삭제할 수 있습니다.
            PENDING_CONFIRMATION 또는 CONFIRMED 상태는 입금 데이터 보호를 위해 삭제할 수 없습니다.
            삭제된 보탬과 롤링페이퍼 메시지는 일반 조회 및 집계에서 제외됩니다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(
            responseCode = "200",
            description = "삭제 성공",
            content = [Content(examples = [ExampleObject(value = SUCCESS_EXAMPLE)])],
        ),
        SwaggerApiResponse(
            responseCode = "401",
            description = "접근 토큰 누락·만료·위조 또는 다른 펀딩의 토큰",
            content = [Content(examples = [ExampleObject(value = TOKEN_INVALID_EXAMPLE)])],
        ),
        SwaggerApiResponse(
            responseCode = "404",
            description = "참여 내역 없음",
            content = [Content(examples = [ExampleObject(value = CONTRIBUTION_NOT_FOUND_EXAMPLE)])],
        ),
        SwaggerApiResponse(
            responseCode = "409",
            description = "송금 확인 중이거나 확인된 보탬",
            content = [Content(examples = [ExampleObject(value = NOT_CANCELLABLE_EXAMPLE)])],
        ),
    )
    fun cancel(
        @Parameter(description = "펀딩 ID") fundingId: Long,
        @Parameter(description = "비회원 보탬 접근 토큰", required = true) accessToken: String?,
    ): ApiResponse<Nothing>

    companion object {
        private const val SUCCESS_EXAMPLE = """{ "success": true, "data": null, "error": null }"""
        private const val ACCESS_REQUEST_EXAMPLE = """
            { "participantName": "지유 친구 민지", "guestPassword": "1234" }
        """
        private const val ACCESS_RESPONSE_EXAMPLE = """
            {
              "success": true,
              "data": {
                "contributionId": 31,
                "participantName": "지유 친구 민지",
                "amount": 10000,
                "status": "PENDING_CONFIRMATION",
                "message": "생일 축하해!",
                "paperTemplateCode": "BLUE_DOT_GRID",
                "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
                "accessTokenExpiresAt": "2026-11-18T12:30:00Z"
              },
              "error": null
            }
        """
        private const val UPDATE_REQUEST_EXAMPLE = """
            {
              "participantName": "민지",
              "message": "지유야 생일 정말 축하해!",
              "paperTemplateCode": "BLACK_CONFETTI"
            }
        """
        private const val UPDATE_RESPONSE_EXAMPLE = """
            {
              "success": true,
              "data": {
                "contributionId": 31,
                "participantName": "민지",
                "amount": 10000,
                "status": "PENDING_CONFIRMATION",
                "message": "지유야 생일 정말 축하해!",
                "paperTemplateCode": "BLACK_CONFETTI"
              },
              "error": null
            }
        """
        private const val PASSWORD_MISMATCH_EXAMPLE = """
            { "success": false, "data": null, "error": { "code": "FUNDING_025", "message": "비밀번호가 일치하지 않습니다.", "detail": null } }
        """
        private const val PASSWORD_LOCKED_EXAMPLE = """
            { "success": false, "data": null, "error": { "code": "FUNDING_029", "message": "비밀번호를 여러 번 틀렸어요. 10분 후 다시 시도해 주세요.", "detail": null } }
        """
        private const val FUNDING_NOT_FOUND_EXAMPLE = """
            { "success": false, "data": null, "error": { "code": "FUNDING_002", "message": "펀딩을 찾을 수 없습니다.", "detail": null } }
        """
        private const val TOKEN_INVALID_EXAMPLE = """
            { "success": false, "data": null, "error": { "code": "FUNDING_033", "message": "비회원 보탬 접근 토큰이 올바르지 않습니다.", "detail": null } }
        """
        private const val CONTRIBUTION_NOT_FOUND_EXAMPLE = """
            { "success": false, "data": null, "error": { "code": "FUNDING_023", "message": "참여 내역을 찾을 수 없습니다.", "detail": null } }
        """
        private const val NAME_DUPLICATED_EXAMPLE = """
            { "success": false, "data": null, "error": { "code": "FUNDING_036", "message": "이미 사용 중인 참여자 이름으로 변경할 수 없습니다.", "detail": null } }
        """
        private const val NOT_CANCELLABLE_EXAMPLE = """
            { "success": false, "data": null, "error": { "code": "FUNDING_035", "message": "송금 확인 중이거나 확인된 보탬은 삭제할 수 없습니다.", "detail": null } }
        """
    }
}
