package com.botem.domain.funding.controller.docs

import com.botem.domain.funding.dto.request.CreateContributionRequest
import com.botem.domain.funding.dto.request.MarkTransferSentRequest
import com.botem.domain.funding.dto.response.ContributionResponse
import com.botem.domain.funding.dto.response.TransferSentResponse
import com.botem.global.exception.ErrorCode
import com.botem.global.response.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.parameters.RequestBody
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Contribution", description = "펀딩 참여(보태기) API")
interface ContributionControllerDocs {

    @Operation(
        summary = "펀딩에 금액 보태기",
        description = """
            로그인 또는 비로그인 사용자가 보탤 금액과 메시지를 등록합니다. 참여는 AWAITING_TRANSFER(송금 대기) 상태로 저장됩니다.
            requestKey는 클라이언트가 생성한 고유 값(UUID 권장, 최대 64자)이며, 같은 펀딩에 같은 키·같은 금액으로 다시 요청하면
            새로 만들지 않고 기존 참여를 그대로 반환합니다. 같은 키로 금액이 다르거나 다른 참여자가 요청하면 FUNDING_027을 반환합니다.
            participantName은 비로그인 시 필수이고, 로그인 시 생략하면 회원 닉네임을 사용합니다.
            guestPassword는 비로그인 시 숫자 4자리로 필수이며 해시로 저장합니다. 로그인 시에는 저장하지 않습니다.
            message(축하 메시지, 최대 460자)와 paperTemplateCode(편지지)는 필수이며 롤링페이퍼로 함께 저장합니다.
            amount는 0원보다 크고 남은 목표 금액(목표 금액 − 입금 확인 완료 금액) 이하여야 합니다.
            입력 금액이 남은 금액보다 크면 FUNDING_022를 반환하고 error.detail에 남은 금액(원, 숫자 문자열)을 담습니다.
            남은 금액이 0원이면 FUNDING_021을 반환합니다. 두 경우 모두 아무것도 저장하지 않습니다.
            마감된 펀딩이나 목표 금액을 이미 달성한 펀딩에는 보탤 수 없습니다.
            응답의 payoutAccount는 송금할 계좌(은행, 복호화된 계좌번호, 예금주)입니다.
            Authorization 헤더 없이 호출하면 비로그인으로 처리하고, 헤더의 토큰이 만료되었거나 유효하지 않으면 401을 반환합니다.
        """,
    )
    @RequestBody(
        required = true,
        content = [
            Content(
                examples = [
                    ExampleObject(name = "비로그인", value = CONTRIBUTE_GUEST_REQUEST_EXAMPLE),
                    ExampleObject(name = "로그인 + 이름 생략", value = CONTRIBUTE_MEMBER_REQUEST_EXAMPLE),
                ],
            ),
        ],
    )
    @ApiResponses(
        SwaggerApiResponse(
            responseCode = "201",
            description = "참여 생성 성공 또는 같은 requestKey의 기존 참여",
            content = [Content(examples = [ExampleObject(value = CONTRIBUTE_RESPONSE_EXAMPLE)])],
        ),
        SwaggerApiResponse(
            responseCode = "400",
            description = "입력 오류",
            content = [Content(examples = [ExampleObject(value = INVALID_INPUT_EXAMPLE)])],
        ),
        SwaggerApiResponse(responseCode = "401", description = "보낸 토큰이 만료되었거나 유효하지 않음"),
        SwaggerApiResponse(
            responseCode = "404",
            description = "펀딩 없음 또는 취소된 펀딩",
            content = [Content(examples = [ExampleObject(value = FUNDING_NOT_FOUND_EXAMPLE)])],
        ),
        SwaggerApiResponse(
            responseCode = "409",
            description = "마감, 목표 달성, 남은 목표 금액 초과 또는 이미 사용된 requestKey",
            content = [
                Content(
                    examples = [
                        ExampleObject(name = "FUNDING_020", summary = "마감된 펀딩", value = FUNDING_CLOSED_EXAMPLE),
                        ExampleObject(
                            name = "FUNDING_022",
                            summary = "남은 목표 금액 초과",
                            value = AMOUNT_EXCEEDS_REMAINING_EXAMPLE,
                        ),
                        ExampleObject(name = "FUNDING_021", summary = "목표 달성", value = TARGET_REACHED_EXAMPLE),
                        ExampleObject(
                            name = "FUNDING_027",
                            summary = "이미 사용된 요청 키",
                            value = REQUEST_KEY_CONFLICT_EXAMPLE,
                        ),
                    ],
                ),
            ],
        ),
    )
    fun contribute(
        @Parameter(hidden = true) userId: Long?,
        @Parameter(hidden = true) tokenErrorCode: ErrorCode?,
        @Parameter(description = "펀딩 ID") fundingId: Long,
        request: CreateContributionRequest,
    ): ApiResponse<ContributionResponse>

    @Operation(
        summary = "송금 완료 표시",
        description = """
            참여자가 '보냈어요'를 누르면 참여를 PENDING_CONFIRMATION(입금 확인 대기)으로 바꿉니다.
            서버는 실제 송금 여부를 확인하지 않으며, 최종 확정은 개설자의 입금 확인으로 결정됩니다.
            로그인 참여는 참여한 회원 본인만, 비로그인 참여는 참여 시 입력한 guestPassword가 일치해야 변경할 수 있습니다.
            비로그인 참여의 비밀번호를 5회 연속 틀리면 10분 동안 FUNDING_029(429)를 반환하며, 성공하면 실패 횟수를 초기화합니다.
            이미 송금 완료로 표시했거나 확인이 끝난 참여는 상태를 바꾸지 않고 현재 상태를 반환합니다.
            송금 전에 보태기가 마감되어 메시지 전용으로 바뀐 참여는 FUNDING_028을 반환합니다.
            마감 후에도 송금 완료 표시는 가능합니다.
        """,
    )
    @RequestBody(
        required = true,
        content = [Content(examples = [ExampleObject(value = TRANSFER_SENT_REQUEST_EXAMPLE)])],
    )
    @ApiResponses(
        SwaggerApiResponse(
            responseCode = "200",
            description = "송금 완료 표시 성공",
            content = [Content(examples = [ExampleObject(value = TRANSFER_SENT_RESPONSE_EXAMPLE)])],
        ),
        SwaggerApiResponse(responseCode = "401", description = "보낸 토큰이 만료되었거나 유효하지 않음"),
        SwaggerApiResponse(
            responseCode = "403",
            description = "참여자 본인이 아니거나 비밀번호 불일치",
            content = [
                Content(
                    examples = [
                        ExampleObject(name = "FUNDING_024", summary = "본인 아님", value = PARTICIPANT_ONLY_EXAMPLE),
                        ExampleObject(
                            name = "FUNDING_025",
                            summary = "비밀번호 불일치",
                            value = PASSWORD_MISMATCH_EXAMPLE,
                        ),
                    ],
                ),
            ],
        ),
        SwaggerApiResponse(
            responseCode = "404",
            description = "참여 내역 없음",
            content = [Content(examples = [ExampleObject(value = CONTRIBUTION_NOT_FOUND_EXAMPLE)])],
        ),
        SwaggerApiResponse(
            responseCode = "409",
            description = "메시지 전용으로 바뀌었거나 거절·취소되어 송금 완료로 표시할 수 없는 상태",
            content = [
                Content(
                    examples = [
                        ExampleObject(
                            name = "FUNDING_028",
                            summary = "메시지 전용으로 전환됨",
                            value = CONVERTED_TO_MESSAGE_ONLY_EXAMPLE,
                        ),
                        ExampleObject(
                            name = "FUNDING_026",
                            summary = "표시할 수 없는 상태",
                            value = TRANSFER_NOT_MARKABLE_EXAMPLE,
                        ),
                    ],
                ),
            ],
        ),
        SwaggerApiResponse(
            responseCode = "429",
            description = "비밀번호 연속 실패로 잠김",
            content = [Content(examples = [ExampleObject(value = PASSWORD_LOCKED_EXAMPLE)])],
        ),
    )
    fun markTransferSent(
        @Parameter(hidden = true) userId: Long?,
        @Parameter(hidden = true) tokenErrorCode: ErrorCode?,
        @Parameter(description = "펀딩 ID") fundingId: Long,
        @Parameter(description = "참여 ID") contributionId: Long,
        request: MarkTransferSentRequest,
    ): ApiResponse<TransferSentResponse>

    companion object {
        private const val CONTRIBUTE_GUEST_REQUEST_EXAMPLE = """
            {
              "requestKey": "7f1c2a9e-3b4d-4e5f-8a6b-1c2d3e4f5a6b",
              "participantName": "지유 친구 민지",
              "amount": 10000,
              "message": "생일 축하해!",
              "paperTemplateCode": "BLUE_DOT_GRID",
              "guestPassword": "1234"
            }
        """
        private const val CONTRIBUTE_MEMBER_REQUEST_EXAMPLE = """
            {
              "requestKey": "0a9b8c7d-6e5f-4a3b-2c1d-0e9f8a7b6c5d",
              "amount": 20000,
              "message": "늦었지만 생일 축하해",
              "paperTemplateCode": "BLACK_CONFETTI"
            }
        """
        private const val CONTRIBUTE_RESPONSE_EXAMPLE = """
            {
              "success": true,
              "data": {
                "contributionId": 31,
                "participantName": "지유 친구 민지",
                "amount": 10000,
                "status": "AWAITING_TRANSFER",
                "payoutAccount": {
                  "bank": "TOSS_BANK",
                  "bankName": "토스뱅크",
                  "accountNumber": "100012345678",
                  "accountHolder": "김지유"
                }
              },
              "error": null
            }
        """
        private const val TRANSFER_SENT_REQUEST_EXAMPLE = """
            {
              "guestPassword": "1234"
            }
        """
        private const val TRANSFER_SENT_RESPONSE_EXAMPLE = """
            {
              "success": true,
              "data": {
                "contributionId": 31,
                "status": "PENDING_CONFIRMATION"
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
                "detail": "비회원 비밀번호는 숫자 4자리여야 합니다."
              }
            }
        """
        private const val AMOUNT_EXCEEDS_REMAINING_EXAMPLE = """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "FUNDING_022",
                "message": "보탤 금액은 남은 목표 금액 이하여야 합니다.",
                "detail": "30000"
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
        private const val FUNDING_CLOSED_EXAMPLE = """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "FUNDING_020",
                "message": "마감된 펀딩에는 참여할 수 없습니다.",
                "detail": null
              }
            }
        """
        private const val TARGET_REACHED_EXAMPLE = """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "FUNDING_021",
                "message": "목표 금액을 이미 달성한 펀딩입니다.",
                "detail": null
              }
            }
        """
        private const val REQUEST_KEY_CONFLICT_EXAMPLE = """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "FUNDING_027",
                "message": "이미 다른 요청에 사용된 요청 키입니다.",
                "detail": null
              }
            }
        """
        private const val PARTICIPANT_ONLY_EXAMPLE = """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "FUNDING_024",
                "message": "참여자 본인만 변경할 수 있습니다.",
                "detail": null
              }
            }
        """
        private const val PASSWORD_MISMATCH_EXAMPLE = """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "FUNDING_025",
                "message": "비밀번호가 일치하지 않습니다.",
                "detail": null
              }
            }
        """
        private const val PASSWORD_LOCKED_EXAMPLE = """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "FUNDING_029",
                "message": "비밀번호를 여러 번 틀렸어요. 10분 후 다시 시도해 주세요.",
                "detail": null
              }
            }
        """
        private const val CONTRIBUTION_NOT_FOUND_EXAMPLE = """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "FUNDING_023",
                "message": "참여 내역을 찾을 수 없습니다.",
                "detail": null
              }
            }
        """
        private const val CONVERTED_TO_MESSAGE_ONLY_EXAMPLE = """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "FUNDING_028",
                "message": "보태기가 마감돼서 메시지만 남았어요.",
                "detail": null
              }
            }
        """
        private const val TRANSFER_NOT_MARKABLE_EXAMPLE = """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "FUNDING_026",
                "message": "현재 상태에서는 송금 완료로 표시할 수 없습니다.",
                "detail": null
              }
            }
        """
    }
}
