package com.botem.domain.funding.controller.docs

import com.botem.domain.funding.dto.request.DecideContributionsRequest
import com.botem.domain.funding.dto.request.UndoContributionDecisionsRequest
import com.botem.domain.funding.dto.response.ConfirmAllContributionsResponse
import com.botem.domain.funding.dto.response.ContributionDecisionResponse
import com.botem.domain.funding.dto.response.ContributionDecisionsResponse
import com.botem.domain.funding.dto.response.PendingContributionListResponse
import com.botem.global.response.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.parameters.RequestBody
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Deposit Confirmation", description = "펀딩 입금 확인 API (개설자)")
interface DepositConfirmationControllerDocs {

    @Operation(
        summary = "입금 확인 대기 목록 조회",
        description = """
            참여자가 '보냈어요'를 눌러 확인 대기(PENDING_CONFIRMATION)가 된 참여 목록을 참여 시각 순으로 조회합니다.
            개설자 본인만 조회할 수 있습니다.
            이름·한국 시간(Asia/Seoul) 기준 참여 시각(participatedAt)·보탠 금액(amount)으로 통장 입금 내역을 대조합니다.
            메시지만 남긴 참여, 송금 전 참여, '못 받았어요' 처리된 참여는 포함하지 않습니다.
            '못 받았어요' 처리된 참여는 참여자가 다시 '보냈어요'를 누르면 같은 참여로 이 목록에 다시 올라옵니다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = [Content(examples = [ExampleObject(value = PENDING_LIST_EXAMPLE)])],
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
    )
    fun getPendingContributions(
        @Parameter(hidden = true) userId: Long,
        @Parameter(description = "펀딩 ID") fundingId: Long,
    ): ApiResponse<PendingContributionListResponse>

    @Operation(
        summary = "입금 확인 (받았어요)",
        description = """
            확인 대기 중인 참여를 CONFIRMED로 확정하고 확인 시각을 저장합니다.
            확정 금액이 목표 금액을 넘더라도 이미 송금된 참여이므로 확정합니다.
            결과를 열기 전이라면 이미 확정된 참여에 다시 요청해도 상태를 바꾸지 않고 현재 결과를 반환합니다.
            결과를 열었거나 서프라이즈 선물 준비를 완료한 뒤에는 참여 상태와 관계없이 FUNDING_031을 반환합니다.
            확인 대기가 아닌 참여(송금 전, 미입금 처리, 메시지만 남긴 참여)는 FUNDING_030, 결과를 확정한 펀딩은 FUNDING_031을 반환합니다.
            응답의 progress는 처리 후 확정·대기 달성률과 남은 확인 대기 건수입니다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(
            responseCode = "200",
            description = "확정 성공",
            content = [Content(examples = [ExampleObject(value = CONFIRM_EXAMPLE)])],
        ),
        SwaggerApiResponse(responseCode = "401", description = "인증 실패"),
        SwaggerApiResponse(
            responseCode = "403",
            description = "펀딩 개설자가 아님",
            content = [Content(examples = [ExampleObject(value = FUNDING_OWNER_ONLY_EXAMPLE)])],
        ),
        SwaggerApiResponse(
            responseCode = "404",
            description = "펀딩 또는 참여 없음",
            content = [
                Content(
                    examples = [
                        ExampleObject(name = "FUNDING_002", summary = "펀딩 없음", value = FUNDING_NOT_FOUND_EXAMPLE),
                        ExampleObject(
                            name = "FUNDING_023",
                            summary = "참여 없음",
                            value = CONTRIBUTION_NOT_FOUND_EXAMPLE,
                        ),
                    ],
                ),
            ],
        ),
        SwaggerApiResponse(
            responseCode = "409",
            description = "확인 대기가 아닌 참여 또는 결과를 확정한 펀딩",
            content = [
                Content(
                    examples = [
                        ExampleObject(name = "FUNDING_030", summary = "확인 대기 아님", value = NOT_PENDING_EXAMPLE),
                        ExampleObject(
                            name = "FUNDING_031",
                            summary = "결과를 확정한 펀딩",
                            value = RESULT_ALREADY_OPENED_EXAMPLE,
                        ),
                    ],
                ),
            ],
        ),
    )
    fun confirm(
        @Parameter(hidden = true) userId: Long,
        @Parameter(description = "펀딩 ID") fundingId: Long,
        @Parameter(description = "참여 ID") contributionId: Long,
    ): ApiResponse<ContributionDecisionResponse>

    @Operation(
        summary = "미입금 처리 (못 받았어요)",
        description = """
            확인 대기 중인 참여를 REJECTED('못 받았대요')로 바꾸고 처리 시각을 저장합니다. 달성률과 확인 대기 목록에서는 빠지지만
            참여자 목록·인원·롤링페이퍼에는 그대로 남습니다. 참여자가 다시 '보냈어요'를 누르면 같은 참여가 확인 대기로 돌아옵니다.
            결과를 열기 전이라면 이미 미입금 처리된 참여에 다시 요청해도 상태를 바꾸지 않고 현재 결과를 반환합니다.
            결과를 열었거나 서프라이즈 선물 준비를 완료한 뒤에는 참여 상태와 관계없이 FUNDING_031을 반환합니다.
            확인 대기가 아닌 참여는 FUNDING_030, 결과를 확정한 펀딩은 FUNDING_031을 반환합니다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(
            responseCode = "200",
            description = "미입금 처리 성공",
            content = [Content(examples = [ExampleObject(value = REJECT_EXAMPLE)])],
        ),
        SwaggerApiResponse(responseCode = "401", description = "인증 실패"),
        SwaggerApiResponse(
            responseCode = "403",
            description = "펀딩 개설자가 아님",
            content = [Content(examples = [ExampleObject(value = FUNDING_OWNER_ONLY_EXAMPLE)])],
        ),
        SwaggerApiResponse(
            responseCode = "404",
            description = "펀딩 또는 참여 없음",
            content = [Content(examples = [ExampleObject(value = CONTRIBUTION_NOT_FOUND_EXAMPLE)])],
        ),
        SwaggerApiResponse(
            responseCode = "409",
            description = "확인 대기가 아닌 참여 또는 결과를 확정한 펀딩",
            content = [
                Content(
                    examples = [
                        ExampleObject(name = "FUNDING_030", summary = "확인 대기 아님", value = NOT_PENDING_EXAMPLE),
                        ExampleObject(
                            name = "FUNDING_031",
                            summary = "결과를 확정한 펀딩",
                            value = RESULT_ALREADY_OPENED_EXAMPLE,
                        ),
                    ],
                ),
            ],
        ),
    )
    fun reject(
        @Parameter(hidden = true) userId: Long,
        @Parameter(description = "펀딩 ID") fundingId: Long,
        @Parameter(description = "참여 ID") contributionId: Long,
    ): ApiResponse<ContributionDecisionResponse>

    @Operation(
        summary = "전체 입금 확인 (모두 받았어요)",
        description = """
            확인 대기 중인 참여를 모두 확정합니다. confirmedCount는 이번 요청으로 확정한 건수이며,
            확인 대기가 없으면 0을 반환합니다. 결과를 확정한 펀딩은 FUNDING_031을 반환합니다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(
            responseCode = "200",
            description = "전체 확정 성공",
            content = [Content(examples = [ExampleObject(value = CONFIRM_ALL_EXAMPLE)])],
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
            description = "결과를 확정한 펀딩",
            content = [Content(examples = [ExampleObject(value = RESULT_ALREADY_OPENED_EXAMPLE)])],
        ),
    )
    fun confirmAll(
        @Parameter(hidden = true) userId: Long,
        @Parameter(description = "펀딩 ID") fundingId: Long,
    ): ApiResponse<ConfirmAllContributionsResponse>

    @Operation(
        summary = "선택 입금 처리 (받았어요·못 받았어요)",
        description = """
            개별 선택 또는 전체 선택한 확인 대기 참여를 한 번에 받았어요(CONFIRM) 또는 못 받았어요(REJECT)로 처리합니다.
            요청한 참여는 모두 함께 처리되며, 하나라도 이 펀딩에 없거나 처리할 수 없으면 아무것도 바뀌지 않습니다.
            이미 같은 결과로 처리된 참여는 그대로 둡니다. 한 번에 최대 100건까지 요청할 수 있습니다.
            응답의 changed는 이번 요청으로 상태가 바뀌었는지 여부입니다. 실행 취소에는 changed가 true인 참여만 보내야 합니다.
            받았어요로 확정 금액이 목표에 닿으면 단건 확인과 같이 보태기를 마감합니다.
            처리 직후 10초 안에는 실행 취소 API로 되돌릴 수 있습니다.
        """,
    )
    @RequestBody(
        required = true,
        content = [Content(examples = [ExampleObject(value = DECIDE_REQUEST_EXAMPLE)])],
    )
    @ApiResponses(
        SwaggerApiResponse(
            responseCode = "200",
            description = "처리 성공",
            content = [Content(examples = [ExampleObject(value = DECISIONS_EXAMPLE)])],
        ),
        SwaggerApiResponse(responseCode = "400", description = "보탬 ID 목록이 비었거나 100건 초과, 또는 decision 오류"),
        SwaggerApiResponse(responseCode = "401", description = "인증 실패"),
        SwaggerApiResponse(
            responseCode = "403",
            description = "펀딩 개설자가 아님",
            content = [Content(examples = [ExampleObject(value = FUNDING_OWNER_ONLY_EXAMPLE)])],
        ),
        SwaggerApiResponse(
            responseCode = "404",
            description = "펀딩 없음 또는 이 펀딩에 없는 참여 포함",
            content = [Content(examples = [ExampleObject(value = CONTRIBUTION_NOT_FOUND_EXAMPLE)])],
        ),
        SwaggerApiResponse(
            responseCode = "409",
            description = "확인 대기가 아닌 참여 포함 또는 결과를 확정한 펀딩",
            content = [
                Content(
                    examples = [
                        ExampleObject(name = "FUNDING_030", summary = "확인 대기 아님", value = NOT_PENDING_EXAMPLE),
                        ExampleObject(
                            name = "FUNDING_031",
                            summary = "결과를 확정한 펀딩",
                            value = RESULT_ALREADY_OPENED_EXAMPLE,
                        ),
                    ],
                ),
            ],
        ),
    )
    fun decide(
        @Parameter(hidden = true) userId: Long,
        @Parameter(description = "펀딩 ID") fundingId: Long,
        request: DecideContributionsRequest,
    ): ApiResponse<ContributionDecisionsResponse>

    @Operation(
        summary = "입금 처리 실행 취소",
        description = """
            받았어요·못 받았어요로 처리한 참여를 확인 대기(PENDING_CONFIRMATION)로 되돌립니다. 토스트의 '실행 취소' 버튼에 사용합니다.
            처리한 지 10초가 지난 참여가 하나라도 있으면 FUNDING_056을 반환하고 아무것도 되돌리지 않으므로,
            선택 처리 응답에서 changed가 true인 참여만 보내야 합니다.
            이미 확인 대기인 참여는 그대로 둡니다. 받았어요·못 받았어요가 아닌 참여는 FUNDING_055를 반환합니다.
            결과를 열었거나 서프라이즈 선물 준비를 완료한 뒤에는 FUNDING_031을 반환합니다.
            받았어요로 목표에 닿아 보태기가 마감된 경우, 실행 취소해도 마감과 송금 전 보탬의 메시지 전용 전환은 되돌리지 않습니다.
        """,
    )
    @RequestBody(
        required = true,
        content = [Content(examples = [ExampleObject(value = UNDO_REQUEST_EXAMPLE)])],
    )
    @ApiResponses(
        SwaggerApiResponse(
            responseCode = "200",
            description = "실행 취소 성공",
            content = [Content(examples = [ExampleObject(value = UNDONE_EXAMPLE)])],
        ),
        SwaggerApiResponse(responseCode = "400", description = "보탬 ID 목록이 비었거나 100건 초과"),
        SwaggerApiResponse(responseCode = "401", description = "인증 실패"),
        SwaggerApiResponse(
            responseCode = "403",
            description = "펀딩 개설자가 아님",
            content = [Content(examples = [ExampleObject(value = FUNDING_OWNER_ONLY_EXAMPLE)])],
        ),
        SwaggerApiResponse(
            responseCode = "404",
            description = "펀딩 없음 또는 이 펀딩에 없는 참여 포함",
            content = [Content(examples = [ExampleObject(value = CONTRIBUTION_NOT_FOUND_EXAMPLE)])],
        ),
        SwaggerApiResponse(
            responseCode = "409",
            description = "되돌릴 수 없는 참여, 실행 취소 시간 초과 또는 결과를 확정한 펀딩",
            content = [
                Content(
                    examples = [
                        ExampleObject(name = "FUNDING_055", summary = "처리한 참여 아님", value = NOT_UNDOABLE_EXAMPLE),
                        ExampleObject(name = "FUNDING_056", summary = "실행 취소 시간 초과", value = UNDO_EXPIRED_EXAMPLE),
                        ExampleObject(
                            name = "FUNDING_031",
                            summary = "결과를 확정한 펀딩",
                            value = RESULT_ALREADY_OPENED_EXAMPLE,
                        ),
                    ],
                ),
            ],
        ),
    )
    fun undo(
        @Parameter(hidden = true) userId: Long,
        @Parameter(description = "펀딩 ID") fundingId: Long,
        request: UndoContributionDecisionsRequest,
    ): ApiResponse<ContributionDecisionsResponse>

    companion object {
        private const val DECIDE_REQUEST_EXAMPLE = """
            {
              "contributionIds": [31, 32],
              "decision": "CONFIRM"
            }
        """
        private const val UNDO_REQUEST_EXAMPLE = """
            {
              "contributionIds": [31, 32]
            }
        """
        private const val DECISIONS_EXAMPLE = """
            {
              "success": true,
              "data": {
                "contributions": [
                  { "contributionId": 31, "status": "CONFIRMED", "changed": true },
                  { "contributionId": 32, "status": "CONFIRMED", "changed": false }
                ],
                "progress": {
                  "confirmedPercentage": 70,
                  "pendingPercentage": 0,
                  "pendingContributionCount": 0,
                  "targetReached": false
                }
              },
              "error": null
            }
        """
        private const val UNDONE_EXAMPLE = """
            {
              "success": true,
              "data": {
                "contributions": [
                  { "contributionId": 31, "status": "PENDING_CONFIRMATION", "changed": true },
                  { "contributionId": 32, "status": "PENDING_CONFIRMATION", "changed": true }
                ],
                "progress": {
                  "confirmedPercentage": 40,
                  "pendingPercentage": 30,
                  "pendingContributionCount": 2,
                  "targetReached": false
                }
              },
              "error": null
            }
        """
        private const val NOT_UNDOABLE_EXAMPLE = """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "FUNDING_055",
                "message": "받았어요·못 받았어요를 처리한 보탬만 되돌릴 수 있습니다.",
                "detail": null
              }
            }
        """
        private const val UNDO_EXPIRED_EXAMPLE = """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "FUNDING_056",
                "message": "실행 취소 가능 시간이 지났습니다.",
                "detail": null
              }
            }
        """
        private const val PENDING_LIST_EXAMPLE = """
            {
              "success": true,
              "data": {
                "pendingContributionCount": 2,
                "contributions": [
                  {
                    "contributionId": 31,
                    "participantName": "하린",
                    "profileImageUrl": "https://image.example.com/profile.jpg",
                    "participatedAt": "2026-11-20T14:03:12",
                    "amount": 45000
                  },
                  {
                    "contributionId": 32,
                    "participantName": "도윤",
                    "profileImageUrl": null,
                    "participatedAt": "2026-11-21T09:41:00",
                    "amount": 30000
                  }
                ]
              },
              "error": null
            }
        """
        private const val CONFIRM_EXAMPLE = """
            {
              "success": true,
              "data": {
                "contributionId": 31,
                "status": "CONFIRMED",
                "progress": {
                  "confirmedPercentage": 55,
                  "pendingPercentage": 15,
                  "pendingContributionCount": 1,
                  "targetReached": false
                }
              },
              "error": null
            }
        """
        private const val REJECT_EXAMPLE = """
            {
              "success": true,
              "data": {
                "contributionId": 32,
                "status": "REJECTED",
                "progress": {
                  "confirmedPercentage": 55,
                  "pendingPercentage": 0,
                  "pendingContributionCount": 0,
                  "targetReached": false
                }
              },
              "error": null
            }
        """
        private const val CONFIRM_ALL_EXAMPLE = """
            {
              "success": true,
              "data": {
                "confirmedCount": 2,
                "progress": {
                  "confirmedPercentage": 70,
                  "pendingPercentage": 0,
                  "pendingContributionCount": 0,
                  "targetReached": false
                }
              },
              "error": null
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
        private const val NOT_PENDING_EXAMPLE = """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "FUNDING_030",
                "message": "입금 확인 대기 중인 참여만 처리할 수 있습니다.",
                "detail": null
              }
            }
        """
        private const val RESULT_ALREADY_OPENED_EXAMPLE = """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "FUNDING_031",
                "message": "결과를 확정한 펀딩은 입금 확인 상태를 바꿀 수 없습니다.",
                "detail": null
              }
            }
        """
    }
}
