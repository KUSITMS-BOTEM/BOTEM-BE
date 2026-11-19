package com.botem.domain.funding.controller.docs

import com.botem.domain.funding.dto.request.UpdateMyContributionRequest
import com.botem.domain.funding.dto.response.MyContributionListResponse
import com.botem.domain.funding.dto.response.MyContributionResponse
import com.botem.global.response.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.parameters.RequestBody
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "My Contribution", description = "회원 내 보탬 API")
interface MyContributionControllerDocs {

    @Operation(
        summary = "내 보탬 조회",
        description = """
            로그인 회원이 이 펀딩에 남긴 보탬을 참여 순서로 조회합니다(JOIN-BS-006). 취소된 보탬은 제외합니다.
            보탬이 없으면 contributions는 빈 배열이며, 이 경우 보태기 버튼을, 있으면 '내 보탬 수정하기'를 안내합니다.
            editable은 메시지를 받을 수 있는 동안(마감일 전) true이며, 그동안 이름·메시지·편지지를 수정할 수 있습니다.
            생일 펀딩은 조기 마감해도 마감일까지 수정할 수 있고, 서프라이즈는 조기 마감하면 메시지와 함께 수정도 닫힙니다.
            status가 REJECTED이면 개설자가 '못 받았어요'를 누른 보탬('못 받았대요')이며, 송금 완료 표시 API로 다시 '보냈어요'를 누를 수 있습니다.
            각 보탬에는 롤링페이퍼의 메시지·편지지·스티커·사진·음성 정보가 포함됩니다.
            payoutAccount는 송금 전(AWAITING_TRANSFER)이거나 REJECTED인 보탬에만 반환하고, 그 외에는 null입니다.
            excludedFromResult는 개설자가 확인하지 않은 채 팡!(서프라이즈는 선물 준비 완료)을 열어 결과에서 빠진 확인 대기 보탬이면 true입니다.
            비회원 보탬은 비회원 보탬 조회 API를 사용합니다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = [Content(examples = [ExampleObject(value = MY_CONTRIBUTIONS_EXAMPLE)])],
        ),
        SwaggerApiResponse(responseCode = "401", description = "인증 실패"),
        SwaggerApiResponse(
            responseCode = "403",
            description = "공개 전 서프라이즈를 받는 사람이 조회",
            content = [Content(examples = [ExampleObject(value = SURPRISE_NOT_REVEALED_EXAMPLE)])],
        ),
        SwaggerApiResponse(
            responseCode = "404",
            description = "펀딩 없음 또는 취소된 펀딩",
            content = [Content(examples = [ExampleObject(value = FUNDING_NOT_FOUND_EXAMPLE)])],
        ),
    )
    fun getMyContributions(
        @Parameter(hidden = true) userId: Long,
        @Parameter(description = "펀딩 ID") fundingId: Long,
    ): ApiResponse<MyContributionListResponse>

    @Operation(
        summary = "내 보탬 수정",
        description = """
            로그인 회원이 자기 보탬의 보여질 이름, 메시지, 편지지를 수정합니다. 금액은 수정할 수 없습니다.
            메시지로 보태기와 같은 기준으로 마감일 전까지 수정할 수 있습니다.
            생일 펀딩은 조기 마감했거나 목표를 달성해도 마감일 전이면 수정할 수 있고, 서프라이즈는 조기 마감하면 수정할 수 없습니다.
            응답의 payoutAccount는 항상 null입니다.
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
            content = [Content(examples = [ExampleObject(value = UPDATED_EXAMPLE)])],
        ),
        SwaggerApiResponse(responseCode = "400", description = "이름·메시지·편지지 입력 오류"),
        SwaggerApiResponse(responseCode = "401", description = "인증 실패"),
        SwaggerApiResponse(
            responseCode = "403",
            description = "내 보탬이 아니거나 공개 전 서프라이즈를 받는 사람이 요청",
            content = [
                Content(
                    examples = [
                        ExampleObject(
                            name = "FUNDING_024",
                            summary = "내 보탬이 아님",
                            value = PARTICIPANT_ONLY_EXAMPLE,
                        ),
                        ExampleObject(
                            name = "FUNDING_046",
                            summary = "공개 전 서프라이즈",
                            value = SURPRISE_NOT_REVEALED_EXAMPLE,
                        ),
                    ],
                ),
            ],
        ),
        SwaggerApiResponse(
            responseCode = "404",
            description = "펀딩 또는 보탬 없음",
            content = [
                Content(
                    examples = [
                        ExampleObject(name = "FUNDING_002", summary = "펀딩 없음", value = FUNDING_NOT_FOUND_EXAMPLE),
                        ExampleObject(
                            name = "FUNDING_023",
                            summary = "보탬 없음",
                            value = CONTRIBUTION_NOT_FOUND_EXAMPLE,
                        ),
                    ],
                ),
            ],
        ),
        SwaggerApiResponse(
            responseCode = "409",
            description = "마감일이 지났거나 서프라이즈를 조기 마감함",
            content = [Content(examples = [ExampleObject(value = NOT_EDITABLE_EXAMPLE)])],
        ),
    )
    fun updateMyContribution(
        @Parameter(hidden = true) userId: Long,
        @Parameter(description = "펀딩 ID") fundingId: Long,
        @Parameter(description = "보탬 ID") contributionId: Long,
        request: UpdateMyContributionRequest,
    ): ApiResponse<MyContributionResponse>

    companion object {
        private const val MY_CONTRIBUTIONS_EXAMPLE = """
            {
              "success": true,
              "data": {
                "editable": true,
                "contributions": [
                  {
                    "contributionId": 21,
                    "participantName": "민지",
                    "amount": 30000,
                    "status": "REJECTED",
                    "excludedFromResult": false,
                    "message": "지유야 생일 축하해!",
                    "paperTemplateCode": "LIME_DOT_GRID",
                    "decorationCode": "PARTY_POPPER",
                    "photoUrl": "https://botem-bucket.s3.ap-northeast-2.amazonaws.com/rolling-paper/7/photo/3f2b8c1e-7a4d-4e9b-9c2a-1d5e6f7a8b9c.jpg",
                    "voiceUrl": null,
                    "voiceDurationSeconds": null,
                    "participatedAt": "2026-10-13T11:20:00",
                    "payoutAccount": {
                      "bank": "TOSS_BANK",
                      "bankName": "토스뱅크",
                      "accountNumber": "100159014821",
                      "accountHolder": null
                    }
                  }
                ]
              },
              "error": null
            }
        """
        private const val UPDATE_REQUEST_EXAMPLE = """
            {
              "participantName": "민지",
              "message": "지유야 생일 진짜 축하해!",
              "paperTemplateCode": "BLUE_DOT_GRID"
            }
        """
        private const val UPDATED_EXAMPLE = """
            {
              "success": true,
              "data": {
                "contributionId": 21,
                "participantName": "민지",
                "amount": 30000,
                "status": "PENDING_CONFIRMATION",
                "excludedFromResult": false,
                "message": "지유야 생일 진짜 축하해!",
                "paperTemplateCode": "BLUE_DOT_GRID",
                "decorationCode": "PARTY_POPPER",
                "photoUrl": "https://botem-bucket.s3.ap-northeast-2.amazonaws.com/rolling-paper/7/photo/3f2b8c1e-7a4d-4e9b-9c2a-1d5e6f7a8b9c.jpg",
                "voiceUrl": null,
                "voiceDurationSeconds": null,
                "participatedAt": "2026-10-13T11:20:00",
                "payoutAccount": null
              },
              "error": null
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
        private const val NOT_EDITABLE_EXAMPLE = """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "FUNDING_048",
                "message": "현재 펀딩 상태에서는 보탬을 수정할 수 없습니다.",
                "detail": null
              }
            }
        """
    }
}
