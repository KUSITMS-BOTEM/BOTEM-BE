package com.botem.domain.funding.controller.docs

import com.botem.domain.funding.dto.request.ChangeFundingProductRequest
import com.botem.domain.funding.dto.request.CreateBirthdayFundingRequest
import com.botem.domain.funding.dto.request.CreateSurpriseFundingRequest
import com.botem.domain.funding.dto.request.ExtendFundingRequest
import com.botem.domain.funding.dto.response.CreateFundingResponse
import com.botem.domain.funding.dto.response.FundingDetailResponse
import com.botem.domain.funding.dto.response.FundingEarlyCloseResponse
import com.botem.domain.funding.dto.response.FundingExtensionResponse
import com.botem.domain.funding.dto.response.FundingProductChangeResponse
import com.botem.domain.funding.dto.response.FundingResultResponse
import com.botem.domain.funding.dto.response.GiftChoiceResponse
import com.botem.domain.funding.dto.response.OwnerFundingDetailResponse
import com.botem.domain.funding.dto.response.SurpriseGiftPreparationResponse
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

@Tag(name = "Funding", description = "펀딩 API")
interface FundingControllerDocs {

    @Operation(
        summary = "일반 생일 펀딩 개설",
        description = """
            로그인 사용자의 위시 상품과 받을 계좌를 스냅샷으로 저장하여 일반 생일 펀딩을 개설합니다.
            받을 계좌는 bank(지원 은행 코드)와 accountNumber로 받으며, 대표계좌를 사용하는 경우에도 같은 방식으로 보냅니다.
            계좌번호는 숫자, 공백, 하이픈만 허용하고 숫자 8~20자리여야 합니다. 입력한 계좌는 내 계좌 목록에 등록되지 않습니다.
            마감일은 선택한 날짜의 마지막 시각으로 저장됩니다.
            진행 중인 일반 펀딩과 공개 대기 중인 서프라이즈를 합쳐 최대 5개까지 개설할 수 있습니다.
            shareUrl은 공유 링크(`/share/{shareKey}`)이고, shareKey는 참여자용 API(`/api/fundings/share/{shareKey}/...`) 경로에 씁니다.
        """,
    )
    @RequestBody(
        required = true,
        content = [Content(examples = [ExampleObject(value = CREATE_FUNDING_REQUEST_EXAMPLE)])],
    )
    @ApiResponses(
        SwaggerApiResponse(
            responseCode = "201",
            description = "개설 성공",
            content = [Content(examples = [ExampleObject(value = CREATE_FUNDING_RESPONSE_EXAMPLE)])],
        ),
        SwaggerApiResponse(
            responseCode = "400",
            description = "목표 금액, 마감일, 사연, 공약 또는 계좌번호 입력 오류",
            content = [
                Content(
                    examples = [
                        ExampleObject(name = "COMMON_001", summary = "입력값 오류", value = INVALID_INPUT_EXAMPLE),
                        ExampleObject(
                            name = "PAYOUT_ACCOUNT_004",
                            summary = "계좌번호 형식 오류",
                            value = INVALID_ACCOUNT_NUMBER_EXAMPLE,
                        ),
                    ],
                ),
            ],
        ),
        SwaggerApiResponse(responseCode = "401", description = "인증 실패"),
        SwaggerApiResponse(
            responseCode = "404",
            description = "위시 없음",
            content = [Content(examples = [ExampleObject(value = WISH_NOT_FOUND_EXAMPLE)])],
        ),
        SwaggerApiResponse(
            responseCode = "409",
            description = "사용 중인 위시 또는 진행 중 펀딩 개수 초과",
            content = [
                Content(
                    examples = [
                        ExampleObject(name = "WISH_003", summary = "사용 중인 위시", value = WISH_IN_FUNDING_EXAMPLE),
                        ExampleObject(
                            name = "FUNDING_019",
                            summary = "진행 중 펀딩 5개",
                            value = ACTIVE_FUNDING_LIMIT_EXCEEDED_EXAMPLE,
                        ),
                    ],
                ),
            ],
        ),
    )
    fun createBirthdayFunding(
        @Parameter(hidden = true) userId: Long,
        request: CreateBirthdayFundingRequest,
    ): ApiResponse<CreateFundingResponse>

    @Operation(
        summary = "서프라이즈 펀딩 개설",
        description = """
            친구(recipientId)의 위시 상품으로 공개 시각이 정해진 서프라이즈 펀딩을 개설합니다. 제목과 사연은 받지 않습니다.
            상품은 받는 사람의 위시를 스냅샷으로 저장합니다.
            받을 계좌는 일반 펀딩과 같이 bank(지원 은행 코드)와 accountNumber로 받으며, 대표계좌를 사용하는 경우에도 같은 방식으로 보냅니다.
            계좌번호는 숫자, 공백, 하이픈만 허용하고 숫자 8~20자리여야 합니다. 입력한 계좌는 내 계좌 목록에 등록되지 않습니다.
            마감일과 공개 시각은 받는 사람의 다음 생일을 기준으로 서버가 자동 계산합니다.
            마감일은 생일 전날의 마지막 시각, 공개 시각은 생일 당일 00:00으로 저장됩니다.
            받는 사람의 생일이 오늘 또는 내일이면 충분한 모금 기간을 확보할 수 없어 개설할 수 없습니다.
            showOnRecipientProfile(위시 프로필에 몰래 알리기)과 notifyRecipientFriends(보탬 친구에게도 알리기)는 생략하면 true이며,
            받는 사람 계정에는 노출하지 않습니다. 이번 API는 옵션만 저장하고 알림은 보내지 않습니다.
            받는 사람 한 명당 공개 전 서프라이즈는 하나만 열 수 있고(공개 시각이 지나면 새로 열 수 있음), 진행 중인 펀딩은 일반 펀딩과 합쳐 최대 5개입니다.
            서프라이즈는 개설 후 취소할 수 없습니다. shareUrl은 비밀 초대에 쓰는 펀딩 링크이고, shareKey는 참여자용 API 경로에 쓰는 공유 키입니다.
            응답의 deadline·revealAt은 서버가 받는 사람 생일로 계산한 마감 시각과 공개 시각으로, 개설 완료 화면에 그대로 보여줍니다.
        """,
    )
    @RequestBody(
        required = true,
        content = [Content(examples = [ExampleObject(value = CREATE_SURPRISE_FUNDING_REQUEST_EXAMPLE)])],
    )
    @ApiResponses(
        SwaggerApiResponse(
            responseCode = "201",
            description = "개설 성공",
            content = [Content(examples = [ExampleObject(value = CREATE_SURPRISE_FUNDING_RESPONSE_EXAMPLE)])],
        ),
        SwaggerApiResponse(
            responseCode = "400",
            description = "목표 금액·계좌번호 입력 오류, 받는 사람 생일 정보 오류 또는 자기 자신을 받는 사람으로 지정",
            content = [
                Content(
                    examples = [
                        ExampleObject(
                            name = "FUNDING_053",
                            summary = "받는 사람 생일 미등록",
                            value = SURPRISE_RECIPIENT_BIRTHDAY_REQUIRED_EXAMPLE,
                        ),
                        ExampleObject(
                            name = "FUNDING_054",
                            summary = "받는 사람 생일 임박",
                            value = SURPRISE_RECIPIENT_BIRTHDAY_TOO_SOON_EXAMPLE,
                        ),
                        ExampleObject(
                            name = "PAYOUT_ACCOUNT_004",
                            summary = "계좌번호 형식 오류",
                            value = INVALID_ACCOUNT_NUMBER_EXAMPLE,
                        ),
                        ExampleObject(
                            name = "FUNDING_044",
                            summary = "자기 자신에게 개설",
                            value = SURPRISE_SELF_RECIPIENT_EXAMPLE,
                        ),
                    ],
                ),
            ],
        ),
        SwaggerApiResponse(responseCode = "401", description = "인증 실패"),
        SwaggerApiResponse(
            responseCode = "404",
            description = "받는 사람(없거나 탈퇴) 또는 받는 사람의 위시 없음",
            content = [
                Content(
                    examples = [
                        ExampleObject(name = "USER_001", summary = "받는 사람 없음", value = USER_NOT_FOUND_EXAMPLE),
                        ExampleObject(name = "WISH_002", summary = "위시 없음", value = WISH_NOT_FOUND_EXAMPLE),
                    ],
                ),
            ],
        ),
        SwaggerApiResponse(
            responseCode = "409",
            description = "공개 전 서프라이즈 존재, 사용 중인 위시 또는 진행 중 펀딩 개수 초과",
            content = [
                Content(
                    examples = [
                        ExampleObject(
                            name = "FUNDING_045",
                            summary = "공개 전 서프라이즈 존재",
                            value = SURPRISE_FUNDING_ALREADY_EXISTS_EXAMPLE,
                        ),
                        ExampleObject(name = "WISH_003", summary = "사용 중인 위시", value = WISH_IN_FUNDING_EXAMPLE),
                        ExampleObject(
                            name = "FUNDING_019",
                            summary = "진행 중 펀딩 5개",
                            value = ACTIVE_FUNDING_LIMIT_EXCEEDED_EXAMPLE,
                        ),
                    ],
                ),
            ],
        ),
    )
    fun createSurpriseFunding(
        @Parameter(hidden = true) userId: Long,
        request: CreateSurpriseFundingRequest,
    ): ApiResponse<CreateFundingResponse>

    @Operation(
        summary = "참여자용 펀딩 상세 조회",
        description = """
            공유 링크로 펀딩 정보와 진행 상태를 조회합니다. 로그인하지 않아도 조회할 수 있습니다.
            펀딩은 공유 링크의 공유 키(32자리)로 찾으며, 순차 펀딩 ID로는 조회할 수 없습니다. 없거나 형식이 틀린 키는 FUNDING_002입니다.
            모인 금액은 노출하지 않고 확정 달성률(입금 확인 완료)과 확인 대기 달성률만 반환하며, 목표 금액(targetAmount)은 반환합니다.
            celebrantName·celebrantProfileImageUrl은 '{이름}의 생일' 제목에 쓰는 주인공으로, 생일 펀딩은 개설자, 서프라이즈는 받는 사람입니다(이름이 없으면 닉네임).
            extended는 기간을 연장한 펀딩이면 true입니다('기간이 연장됐어요' 배지).
            참여자 목록은 취소된 참여를 제외하고 첫 참여 순서대로 반환합니다. 로그인 참여자는 한 번만,
            비로그인 참여는 각각 한 명으로 집계하며 메시지만 남긴 참여자도 포함합니다.
            order는 참여 순서(1부터)이며 역할 배지 문구는 이 값으로 결정합니다.
            contributedDate는 해당 참여가 처음 생성된 날짜이며 yyyy-MM-dd 형식으로 반환합니다.
            displayStatus는 IN_PROGRESS(진행 중), TARGET_REACHED(목표 금액 달성), CLOSED(마감),
            GIFT_PREPARING(선물 준비 중), COMPLETED(인증 완료) 중 하나입니다.
            목표 금액 달성 후에는 canContributeAmount가 false, 마감 후에는 canParticipate도 false입니다.
            조기 마감된 일반 펀딩은 canContributeAmount가 false지만 기존 마감일까지 canParticipate는 true입니다.
            productChanged는 상품 변경 이력 존재 여부이며 productChangedAt은 마지막 상품 변경 시각입니다.
            remainingDays는 마감일까지 남은 일수(마감일 당일 0)이며 마감 후에는 null입니다.
            ownerPreview는 로그인한 개설자 본인이 조회한 경우 true입니다. 취소된 펀딩은 404를 반환합니다.
            giftVerification은 선물 인증을 마친 경우(COMPLETED)에만 사진, 감사 메시지, 완료 시각을 반환하고 그 외에는 null입니다.
            Authorization 헤더 없이 호출하면 비로그인으로 조회하고, 헤더의 토큰이 만료되었거나 유효하지 않으면 401을 반환합니다.
            title은 제목 입력이 없어진 뒤 개설된 펀딩과 서프라이즈 펀딩에서는 null입니다.
            서프라이즈를 받는 사람이 로그인 상태로 공개 시각 전에 조회하면 403(FUNDING_046)을 반환합니다.
            공개 시각이 지났더라도 주최자가 선물 준비를 완료하기 전이면 같은 403(FUNDING_046)을 반환합니다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = [Content(examples = [ExampleObject(value = DETAIL_EXAMPLE)])],
        ),
        SwaggerApiResponse(
            responseCode = "403",
            description = "공개 전 서프라이즈를 받는 사람이 조회",
            content = [Content(examples = [ExampleObject(value = SURPRISE_NOT_REVEALED_EXAMPLE)])],
        ),
        SwaggerApiResponse(
            responseCode = "401",
            description = "보낸 토큰이 만료되었거나 유효하지 않음",
            content = [Content(examples = [ExampleObject(value = EXPIRED_TOKEN_EXAMPLE)])],
        ),
        SwaggerApiResponse(
            responseCode = "404",
            description = "펀딩 없음 또는 취소된 펀딩",
            content = [Content(examples = [ExampleObject(value = FUNDING_NOT_FOUND_EXAMPLE)])],
        ),
    )
    fun getFundingDetail(
        @Parameter(hidden = true) userId: Long?,
        @Parameter(hidden = true) tokenErrorCode: ErrorCode?,
        @Parameter(description = "펀딩 공유 키 (공유 링크의 32자리 값)") shareKey: String,
    ): ApiResponse<FundingDetailResponse>

    @Operation(
        summary = "개설자용 펀딩 관리 상세 조회",
        description = """
            펀딩 개설자가 진행률, 확인 대기 건수, 참여자와 메시지 현황을 확인합니다. 개설자 본인만 조회할 수 있습니다.
            title은 제목 입력이 없어진 뒤 개설된 펀딩과 서프라이즈 펀딩에서는 null입니다.
            확정·대기 달성률은 홈 펀딩 카드와 같은 기준(소수점 버림)이며, 참여자별 금액은 반환하지 않습니다.
            participants는 입금 대조를 위해 취소를 제외한 참여 기록마다 한 줄씩 참여 시각 순으로 반환합니다.
            각 참여의 thankCardSent와 thankCardSentAt으로 감사카드 발송 여부와 최초 발송 시각을 확인합니다.
            order는 사람 기준 첫 참여 순서(1부터)로 역할 배지에 쓰며, 같은 회원이 여러 번 보태면 같은 order를 가집니다.
            status는 AWAITING_TRANSFER(송금 전), PENDING_CONFIRMATION(확인 대기), CONFIRMED(확정),
            REJECTED(못 받았어요), MESSAGE_ONLY(메시지로 보탬) 중 하나입니다.
            messageCount는 도착한 롤링페이퍼 메시지 개수이며 메시지 내용은 마감 전까지 반환하지 않습니다.
            remainingDays는 마감일까지 남은 일수(마감일 당일 0)이며 마감 후에는 null입니다.
            revealAt은 서프라이즈 펀딩의 공개 시각이며 생일 펀딩은 null입니다.
            recipientWithdrawn은 서프라이즈 받는 사람이 탈퇴했으면 true입니다('받는 사람이 보탬을 떠났어요'). 탈퇴해도 서프라이즈는 그대로 진행하며, 생일 펀딩은 false입니다.
            giftPreparedAt은 서프라이즈 주최자가 선물 준비를 완료한 시각이며, 준비 전이거나 생일 펀딩은 null입니다.
            managementState는 NO_PARTICIPANTS(참여자 0명), IN_PROGRESS(진행 중), TARGET_REACHED(확정 금액 100% 도달),
            CLOSED(마감) 중 하나이며, extended는 기간을 연장한 적이 있으면 true입니다.
            productChanged와 productChangedAt으로 상품 변경 여부를, earlyClosedAt으로 조기 마감 여부를 확인할 수 있습니다.
            actions는 지금 가능한 관리 동작입니다. 조기 마감·상품 바꾸기·공유 카드 다시 받기는 마감 전 진행 중에만,
            기간 연장은 생일 펀딩이 마감 후 목표에 못 미쳤고 아직 연장한 적이 없을 때만 true입니다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = [Content(examples = [ExampleObject(value = MANAGEMENT_DETAIL_EXAMPLE)])],
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
    fun getFundingManagementDetail(
        @Parameter(hidden = true) userId: Long,
        @Parameter(description = "펀딩 ID") fundingId: Long,
    ): ApiResponse<OwnerFundingDetailResponse>

    @Operation(
        summary = "펀딩 조기 마감",
        description = """
            진행 중인 펀딩을 개설자가 조기 마감합니다. 이미 조기 마감한 펀딩에 다시 요청하면 조기 마감을 다시 수행하지 않으며,
            최초 조기 마감 시각과 현재 펀딩 상태를 반환합니다.
            송금하지 않은 보탬은 금액을 제외하고 메시지만 유지하며, 이후 추가 금액 보태기는 차단됩니다.
            일반 생일 펀딩은 기존 마감일까지 메시지만 추가로 받을 수 있습니다.
            서프라이즈 펀딩은 메시지 참여도 종료하고 GIFT_PREPARING 상태가 되며 기존 공개 시각은 유지합니다.
            이후 선물 준비 완료 API를 호출해야 WAITING_REVEAL 상태가 됩니다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(
            responseCode = "200",
            description = "조기 마감 성공 또는 기존 조기 마감 결과 반환",
            content = [Content(examples = [ExampleObject(value = EARLY_CLOSE_RESPONSE_EXAMPLE)])],
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
            description = "조기 마감할 수 없는 상태",
            content = [Content(examples = [ExampleObject(value = FUNDING_EARLY_CLOSE_NOT_AVAILABLE_EXAMPLE)])],
        ),
    )
    fun closeFundingEarly(
        @Parameter(hidden = true) userId: Long,
        @Parameter(description = "펀딩 ID") fundingId: Long,
    ): ApiResponse<FundingEarlyCloseResponse>

    @Operation(
        summary = "펀딩 상품 변경",
        description = """
            진행 중인 펀딩의 상품을 개설자 본인의 다른 위시 상품으로 변경합니다.
            목표 금액과 기존 참여 내역은 유지하고, 위시 원본은 수정하지 않으며 펀딩 상품 스냅샷만 교체합니다.
            변경 전·후 상품 정보는 이력으로 저장합니다. 같은 위시로 반복 요청하면 이력을 추가하지 않고 현재 결과를 반환합니다.
            다른 종료되지 않은 펀딩에서 사용 중인 위시는 선택할 수 없습니다.
            생일 펀딩은 결과를 연 뒤에도 구매(선물 선택 저장) 전까지 '다른 선물 고르기'로 상품을 바꿀 수 있습니다 (FUND-PG-012).
        """,
    )
    @RequestBody(
        required = true,
        content = [Content(examples = [ExampleObject(value = CHANGE_PRODUCT_REQUEST_EXAMPLE)])],
    )
    @ApiResponses(
        SwaggerApiResponse(
            responseCode = "200",
            description = "상품 변경 성공 또는 동일 요청 결과 반환",
            content = [Content(examples = [ExampleObject(value = CHANGE_PRODUCT_RESPONSE_EXAMPLE)])],
        ),
        SwaggerApiResponse(
            responseCode = "400",
            description = "위시 ID 입력 오류",
            content = [Content(examples = [ExampleObject(value = WISH_ID_INVALID_EXAMPLE)])],
        ),
        SwaggerApiResponse(responseCode = "401", description = "인증 실패"),
        SwaggerApiResponse(
            responseCode = "403",
            description = "펀딩 개설자가 아님",
            content = [Content(examples = [ExampleObject(value = FUNDING_OWNER_ONLY_EXAMPLE)])],
        ),
        SwaggerApiResponse(
            responseCode = "404",
            description = "펀딩, 위시 없음 또는 취소된 펀딩",
            content = [
                Content(
                    examples = [
                        ExampleObject(name = "FUNDING_002", summary = "펀딩 없음", value = FUNDING_NOT_FOUND_EXAMPLE),
                        ExampleObject(name = "WISH_002", summary = "위시 없음", value = WISH_NOT_FOUND_EXAMPLE),
                    ],
                ),
            ],
        ),
        SwaggerApiResponse(
            responseCode = "409",
            description = "사용 중인 위시 또는 상품 변경 불가 상태",
            content = [
                Content(
                    examples = [
                        ExampleObject(name = "WISH_003", summary = "사용 중인 위시", value = WISH_IN_FUNDING_EXAMPLE),
                        ExampleObject(
                            name = "FUNDING_043",
                            summary = "상품 변경 불가 상태",
                            value = FUNDING_PRODUCT_CHANGE_NOT_AVAILABLE_EXAMPLE,
                        ),
                    ],
                ),
            ],
        ),
    )
    fun changeFundingProduct(
        @Parameter(hidden = true) userId: Long,
        @Parameter(description = "펀딩 ID") fundingId: Long,
        request: ChangeFundingProductRequest,
    ): ApiResponse<FundingProductChangeResponse>

    @Operation(
        summary = "펀딩 기간 연장",
        description = """
            마감된 일반 생일 펀딩의 마감일을 한 번 연장합니다. 펀딩 개설자 본인만 요청할 수 있습니다.
            새 마감일은 요청한 날짜의 마지막 시각으로 저장되며, 요청일로부터 최대 14일까지 선택할 수 있습니다.
            목표 금액에 도달하지 않은 펀딩만 연장할 수 있고 기존 참여 내역과 확정 금액은 유지됩니다.
            기존 마감일까지 송금 완료하지 않은 보탬은 메시지만 남기고, 연장 이후 새 보탬으로 다시 참여할 수 있습니다.
            결과를 이미 열었다면 공개된 롤링페이퍼를 유지한 채 다시 진행 상태로 전환합니다.
            연장 기간에는 추가 보태기와 메시지 작성이 가능하지만, 확정 금액이 목표에 도달하면 금액 보태기는 종료됩니다.
            로그인 회원은 연장 이후 보탬만 1인 1회로 세므로 연장 기간에 한 번 더 보탤 수 있습니다.
            비회원은 기존 참여와 같은 이름으로 새 참여를 만들 수 없으며 기존 참여는 발급받은 접근 토큰으로 관리합니다.
        """,
    )
    @RequestBody(
        required = true,
        content = [Content(examples = [ExampleObject(value = EXTEND_FUNDING_REQUEST_EXAMPLE)])],
    )
    @ApiResponses(
        SwaggerApiResponse(
            responseCode = "200",
            description = "기간 연장 성공",
            content = [Content(examples = [ExampleObject(value = EXTEND_FUNDING_RESPONSE_EXAMPLE)])],
        ),
        SwaggerApiResponse(
            responseCode = "400",
            description = "생일 펀딩이 아니거나 날짜 형식 또는 새 마감일이 잘못됨",
            content = [
                Content(
                    examples = [
                        ExampleObject(
                            name = "COMMON_001",
                            summary = "날짜 형식 오류",
                            value = FUNDING_EXTENSION_DATE_FORMAT_INVALID_EXAMPLE,
                        ),
                        ExampleObject(
                            name = "FUNDING_004",
                            summary = "생일 펀딩이 아님",
                            value = BIRTHDAY_FUNDING_REQUIRED_EXAMPLE,
                        ),
                        ExampleObject(
                            name = "FUNDING_040",
                            summary = "새 마감일 오류",
                            value = FUNDING_EXTENSION_DEADLINE_INVALID_EXAMPLE,
                        ),
                        ExampleObject(
                            name = "FUNDING_041",
                            summary = "최대 연장 기간 초과",
                            value = FUNDING_EXTENSION_PERIOD_EXCEEDED_EXAMPLE,
                        ),
                    ],
                ),
            ],
        ),
        SwaggerApiResponse(responseCode = "401", description = "인증 실패"),
        SwaggerApiResponse(
            responseCode = "403",
            description = "펀딩 개설자가 아님",
            content = [Content(examples = [ExampleObject(value = FUNDING_OWNER_ONLY_EXAMPLE)])],
        ),
        SwaggerApiResponse(
            responseCode = "404",
            description = "펀딩 없음",
            content = [Content(examples = [ExampleObject(value = FUNDING_NOT_FOUND_EXAMPLE)])],
        ),
        SwaggerApiResponse(
            responseCode = "409",
            description = "마감 전, 연장할 수 없는 상태, 연장 횟수 초과, 목표 금액 달성 또는 진행 중 펀딩 개수 초과",
            content = [
                Content(
                    examples = [
                        ExampleObject(
                            name = "FUNDING_019",
                            summary = "진행 중 펀딩 5개",
                            value = ACTIVE_FUNDING_LIMIT_EXCEEDED_EXAMPLE,
                        ),
                        ExampleObject(
                            name = "FUNDING_021",
                            summary = "목표 금액 달성",
                            value = FUNDING_TARGET_REACHED_EXAMPLE,
                        ),
                        ExampleObject(
                            name = "FUNDING_037",
                            summary = "마감 전",
                            value = FUNDING_EXTENSION_BEFORE_DEADLINE_EXAMPLE,
                        ),
                        ExampleObject(
                            name = "FUNDING_038",
                            summary = "연장할 수 없는 상태",
                            value = FUNDING_EXTENSION_NOT_AVAILABLE_EXAMPLE,
                        ),
                        ExampleObject(
                            name = "FUNDING_039",
                            summary = "연장 횟수 초과",
                            value = FUNDING_EXTENSION_LIMIT_EXCEEDED_EXAMPLE,
                        ),
                    ],
                ),
            ],
        ),
    )
    fun extendFunding(
        @Parameter(hidden = true) userId: Long,
        @Parameter(description = "펀딩 ID") fundingId: Long,
        request: ExtendFundingRequest,
    ): ApiResponse<FundingExtensionResponse>

    @Operation(
        summary = "마감된 펀딩 결과 조회",
        description = """
            로그인한 펀딩 개설자가 마감된 생일 펀딩의 결과를 조회합니다. 마감 여부는 마감일과 현재 시각으로 판단합니다.
            마감 전이라도 확정 금액이 목표에 닿았고 확인 대기가 0건이면 조회할 수 있습니다(홈 '지금 펑! 열까요?'). 그 외 마감 전에는 FUNDING_005를 반환합니다.
            결과를 열기 전에 처음 조회하면 보태기를 마감하고, 아직 송금 전인 참여는 금액을 빼고 메시지 전용으로 바꿉니다.
            확정 금액은 입금 확인이 완료된 참여만 합산하고, 참여자 수는 취소된 참여를 제외합니다.
            messageCount가 0보다 크면 금액이 0원이어도 롤링페이퍼를 공개하고, participantCount가 0이면 참여자 0명 화면을 보여줍니다.
            pendingCleanupRequired가 true면 기본 팡 열기 요청 전에 확인 대기(pendingContributionCount건)를 정리해야 합니다.
            정리하지 않고 열려면 excludePendingContributions=true로 요청해 확인 대기를 결과에서 제외합니다.
            이 API는 결과 열람 상태를 변경하지 않으며, resultOpenedAt이 null이면 아직 팡을 열지 않은 상태입니다.
            기간을 연장하면 기존 결과 열람 상태가 초기화되며, 새 마감일 전에는 결과를 조회할 수 없습니다.
            contributions는 '고마운 친구들' 목록으로, 취소를 제외한 보탬을 참여 순서대로 한 건마다 한 줄씩 반환합니다.
            같은 회원이 여러 번 보태면 여러 줄이 되며, 금액은 반환하지 않습니다.
            감사카드는 contributionId로 보내며, status가 CONFIRMED이고 thankCardSent가 false인 보탬에만 보낼 수 있습니다.
            hasMessage·hasPhoto·hasVoice는 롤링페이퍼에 글·사진·음성이 있는지 여부이고, thankCardSent는 감사카드를 이미 보냈는지 여부입니다.
            productChanged는 상품을 바꾼 적이 있는지로 '기존 선물'·'변경된 선물' 표시에 사용합니다 (FUND-PG-012).
            canExtend는 '기간 연장' 노출 여부로, 마감 후 목표 미달이고 연장하지 않았으며 아직 선물을 고르지 않았을 때 true입니다.
            giftChosen은 구매(쿠팡 이동)로 선물을 정했는지 여부입니다. 정한 뒤에는 기간 연장과 '다른 선물 고르기'를 할 수 없습니다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = [Content(examples = [ExampleObject(value = RESULT_EXAMPLE)])],
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
            description = "펀딩 없음",
            content = [Content(examples = [ExampleObject(value = FUNDING_NOT_FOUND_EXAMPLE)])],
        ),
        SwaggerApiResponse(
            responseCode = "409",
            description = "마감 전(목표 미달 또는 확인 대기 있음)이거나 결과를 조회할 수 없는 상태",
            content = [
                Content(
                    examples = [
                        ExampleObject(name = "FUNDING_005", summary = "마감 전", value = FUNDING_NOT_ENDED_EXAMPLE),
                        ExampleObject(
                            name = "FUNDING_006",
                            summary = "결과 조회 불가 상태",
                            value = FUNDING_RESULT_NOT_OPENABLE_EXAMPLE,
                        ),
                    ],
                ),
            ],
        ),
    )
    fun getFundingResult(
        @Parameter(hidden = true) userId: Long,
        @Parameter(description = "펀딩 ID") fundingId: Long,
    ): ApiResponse<FundingResultResponse>

    @Operation(
        summary = "펀딩 결과 팡 열기",
        description = """
            마감된 생일 펀딩의 결과를 열고 선물 준비 단계로 전환합니다. 결과를 연 뒤에는 참여 상태와 결과 금액을 바꿀 수 없습니다.
            마감 전이라도 확정 금액이 목표에 닿았고 확인 대기가 0건이면 열 수 있으며, 연 뒤에는 마감일 전이어도 금액 보태기·메시지·보탬 수정이 모두 막힙니다.
            마감 전에는 목표를 달성했어도 확인 대기가 있으면 excludePendingContributions 값과 관계없이 열 수 없고 FUNDING_007을 반환합니다.
            마감 전 목표 미달이면 FUNDING_005를 반환합니다.
            입금 확인 대기 참여가 남아 있으면 FUNDING_007을 반환합니다. 확인 대기는 자동으로 확정하지 않습니다.
            excludePendingContributions=true로 요청하면 남은 확인 대기를 결과 금액에서 제외하고 엽니다.
            제외된 참여는 상태를 그대로 유지해 참여자에게는 '확인 중'으로 보입니다.
            이미 결과를 연 펀딩에 다시 요청하면 상태를 변경하지 않고 기존 결과를 반환합니다.
            단, 기간을 연장한 펀딩은 기존 결과 열람 상태가 초기화되므로 새 마감일 이후 다시 열어야 합니다.
            응답의 contributions는 결과 조회 API와 같습니다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(
            responseCode = "200",
            description = "팡 열기 성공 또는 이미 열린 결과 반환",
            content = [Content(examples = [ExampleObject(value = OPENED_RESULT_EXAMPLE)])],
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
            description = "펀딩 없음",
            content = [Content(examples = [ExampleObject(value = FUNDING_NOT_FOUND_EXAMPLE)])],
        ),
        SwaggerApiResponse(
            responseCode = "409",
            description = "마감 전, 결과 열람 불가 상태 또는 입금 확인 대기 참여 존재",
            content = [
                Content(
                    examples = [
                        ExampleObject(name = "FUNDING_005", summary = "마감 전", value = FUNDING_NOT_ENDED_EXAMPLE),
                        ExampleObject(
                            name = "FUNDING_006",
                            summary = "결과 열람 불가 상태",
                            value = FUNDING_RESULT_NOT_OPENABLE_EXAMPLE,
                        ),
                        ExampleObject(
                            name = "FUNDING_007",
                            summary = "입금 확인 대기 참여 존재",
                            value = PENDING_CONTRIBUTION_EXISTS_EXAMPLE,
                        ),
                    ],
                ),
            ],
        ),
    )
    fun openFundingResult(
        @Parameter(hidden = true) userId: Long,
        @Parameter(description = "펀딩 ID") fundingId: Long,
        @Parameter(description = "남은 확인 대기를 결과에서 제외하고 열지 여부 (기본 false)")
        excludePendingContributions: Boolean,
    ): ApiResponse<FundingResultResponse>

    @Operation(
        summary = "생일 펀딩 선물 선택 저장",
        description = """
            결과를 연 생일 펀딩에서 개설자가 '차액으로 구매'·'구매하기'로 쿠팡에 이동하는 순간 호출합니다 (FUND-PG-011/012).
            지금 펀딩 상품을 선물로 정하고 선물 인증 대기(WAITING_GIFT_VERIFICATION)로 바꿉니다.
            다른 선물로 사려면 먼저 펀딩 상품 변경 API로 상품을 바꾼 뒤 호출합니다.
            선물을 정한 뒤에는 기간 연장과 상품 변경을 할 수 없고, 펀딩은 홈 카드 대신 '인증할 선물' 배너에 표시됩니다.
            응답의 productUrl로 쿠팡에 이동하고, giftVerificationId로 선물 인증을 완료합니다.
            이미 선물을 정한 펀딩에 다시 요청하면 상태를 바꾸지 않고 같은 결과를 반환합니다.
            '24시간 안에 구매 후 인증' 문구는 안내 문구이며 서버 기한은 없습니다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(
            responseCode = "200",
            description = "선물 선택 저장 성공 또는 이미 저장한 결과 반환",
            content = [Content(examples = [ExampleObject(value = GIFT_CHOICE_EXAMPLE)])],
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
            description = "결과를 열기 전이거나 인증을 마친 펀딩",
            content = [Content(examples = [ExampleObject(value = GIFT_CHOICE_NOT_AVAILABLE_EXAMPLE)])],
        ),
    )
    fun chooseGift(
        @Parameter(hidden = true) userId: Long,
        @Parameter(description = "펀딩 ID") fundingId: Long,
    ): ApiResponse<GiftChoiceResponse>

    @Operation(
        summary = "서프라이즈 마감 · 선물 준비 조회",
        description = """
            주최자가 마감된 서프라이즈의 선물 준비 화면 데이터를 조회합니다. 마감일이 지났거나 조기 마감한 경우에만 조회할 수 있습니다.
            처음 조회할 때 보태기를 마감하고, 송금하지 않은 보탬은 메시지 전용으로 바꿉니다.
            목표 금액에 못 미쳐도 실패로 처리하지 않으며, confirmedAmount(확정 금액)만큼 선물을 준비합니다.
            pendingCleanupRequired가 true면 확인 대기 참여를 처리하거나 제외해야 선물 준비를 완료할 수 있습니다.
            선물 준비를 완료한 뒤에도 같은 API로 준비 완료 · 공유 화면 데이터를 조회할 수 있습니다.
            recipientName은 받는 사람의 이름이며, 이름이 없으면 닉네임을 반환합니다.
            recipientWithdrawn은 받는 사람이 탈퇴했으면 true이며, 이때 recipientName은 '탈퇴한 사용자'입니다. 탈퇴해도 선물 준비와 공개 일정은 그대로 진행합니다.
            recipientPayoutAccount는 목표 미달(targetReached=false)인 경우에만 받는 사람의 대표계좌를 반환합니다.
            주최자만 호출할 수 있는 마감된 서프라이즈 API에서만 노출되며, 받는 사람이 탈퇴했거나 대표계좌가 없거나 목표를 달성한 경우에는 null입니다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = [Content(examples = [ExampleObject(value = SURPRISE_GIFT_PREPARATION_EXAMPLE)])],
        ),
        SwaggerApiResponse(
            responseCode = "400",
            description = "서프라이즈 펀딩이 아님",
            content = [Content(examples = [ExampleObject(value = SURPRISE_FUNDING_REQUIRED_EXAMPLE)])],
        ),
        SwaggerApiResponse(responseCode = "401", description = "인증 실패"),
        SwaggerApiResponse(
            responseCode = "403",
            description = "서프라이즈 주최자가 아님",
            content = [Content(examples = [ExampleObject(value = FUNDING_OWNER_ONLY_EXAMPLE)])],
        ),
        SwaggerApiResponse(
            responseCode = "404",
            description = "펀딩 없음",
            content = [Content(examples = [ExampleObject(value = FUNDING_NOT_FOUND_EXAMPLE)])],
        ),
        SwaggerApiResponse(
            responseCode = "409",
            description = "마감 전",
            content = [Content(examples = [ExampleObject(value = FUNDING_NOT_ENDED_EXAMPLE)])],
        ),
    )
    fun getSurpriseGiftPreparation(
        @Parameter(hidden = true) userId: Long,
        @Parameter(description = "펀딩 ID") fundingId: Long,
    ): ApiResponse<SurpriseGiftPreparationResponse>

    @Operation(
        summary = "서프라이즈 선물 준비 완료",
        description = """
            주최자가 마감된 서프라이즈의 선물 준비를 완료합니다. giftPreparedAt을 저장하고 WAITING_REVEAL 상태로 전환합니다.
            공개 시각(revealAt)이 지나면 받는 사람 홈 공개 카드와 서프라이즈 공개 API의 대상이 됩니다.
            준비를 완료하지 않은 서프라이즈는 공개 시각이 지나도 받는 사람에게 공개 카드가 노출되지 않습니다.
            응답의 recipientPayoutAccount는 목표 미달(targetReached=false)이고 받는 사람이 활성 상태이며 대표계좌가 있을 때만 반환합니다.
            목표 달성, 받는 사람 탈퇴 또는 대표계좌 미등록 시에는 null입니다.
            입금 확인 대기 참여가 남아 있으면 FUNDING_007을 반환합니다. 확인 대기는 자동으로 확정하지 않습니다.
            excludePendingContributions=true로 요청하면 남은 확인 대기를 확정 금액에서 제외하고 완료합니다.
            이미 준비를 완료한 서프라이즈에 다시 요청하면 상태를 변경하지 않고 기존 결과를 반환합니다.
            자동 알림은 보내지 않으며, 공유는 응답 데이터로 화면에서 처리합니다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(
            responseCode = "200",
            description = "선물 준비 완료 또는 이미 완료된 결과 반환",
            content = [Content(examples = [ExampleObject(value = SURPRISE_GIFT_PREPARED_EXAMPLE)])],
        ),
        SwaggerApiResponse(
            responseCode = "400",
            description = "서프라이즈 펀딩이 아님",
            content = [Content(examples = [ExampleObject(value = SURPRISE_FUNDING_REQUIRED_EXAMPLE)])],
        ),
        SwaggerApiResponse(responseCode = "401", description = "인증 실패"),
        SwaggerApiResponse(
            responseCode = "403",
            description = "서프라이즈 주최자가 아님",
            content = [Content(examples = [ExampleObject(value = FUNDING_OWNER_ONLY_EXAMPLE)])],
        ),
        SwaggerApiResponse(
            responseCode = "404",
            description = "펀딩 없음",
            content = [Content(examples = [ExampleObject(value = FUNDING_NOT_FOUND_EXAMPLE)])],
        ),
        SwaggerApiResponse(
            responseCode = "409",
            description = "마감 전, 선물 준비 불가 상태 또는 입금 확인 대기 참여 존재",
            content = [
                Content(
                    examples = [
                        ExampleObject(name = "FUNDING_005", summary = "마감 전", value = FUNDING_NOT_ENDED_EXAMPLE),
                        ExampleObject(
                            name = "FUNDING_047",
                            summary = "선물 준비 불가 상태",
                            value = SURPRISE_GIFT_PREPARATION_NOT_AVAILABLE_EXAMPLE,
                        ),
                        ExampleObject(
                            name = "FUNDING_007",
                            summary = "입금 확인 대기 참여 존재",
                            value = PENDING_CONTRIBUTION_EXISTS_EXAMPLE,
                        ),
                    ],
                ),
            ],
        ),
    )
    fun completeSurpriseGiftPreparation(
        @Parameter(hidden = true) userId: Long,
        @Parameter(description = "펀딩 ID") fundingId: Long,
        @Parameter(description = "남은 확인 대기를 확정 금액에서 제외하고 완료할지 여부 (기본 false)")
        excludePendingContributions: Boolean,
    ): ApiResponse<SurpriseGiftPreparationResponse>

    companion object {
        private const val CREATE_SURPRISE_FUNDING_REQUEST_EXAMPLE = """
            {
              "recipientId": 7,
              "wishId": 21,
              "bank": "TOSS_BANK",
              "accountNumber": "1001-5901-4821",
              "targetAmount": 39000,
              "showOnRecipientProfile": true,
              "notifyRecipientFriends": true
            }
        """
        private const val CREATE_SURPRISE_FUNDING_RESPONSE_EXAMPLE = """
            {
              "success": true,
              "data": {
                "fundingId": 13,
                "shareKey": "0123456789abcdef0123456789abcdef",
                "type": "SURPRISE",
                "status": "FUNDING",
                "shareUrl": "https://botem.me/share/0123456789abcdef0123456789abcdef",
                "deadline": "2026-12-12T23:59:59.999999",
                "revealAt": "2026-12-13T00:00:00"
              },
              "error": null
            }
        """
        private const val SURPRISE_RECIPIENT_BIRTHDAY_REQUIRED_EXAMPLE = """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "FUNDING_053",
                "message": "서프라이즈 펀딩을 열려면 받는 사람의 생일 정보가 필요합니다.",
                "detail": null
              }
            }
        """
        private const val SURPRISE_RECIPIENT_BIRTHDAY_TOO_SOON_EXAMPLE = """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "FUNDING_054",
                "message": "받는 사람의 생일이 임박해 서프라이즈 펀딩을 열 수 없습니다.",
                "detail": null
              }
            }
        """
        private const val SURPRISE_SELF_RECIPIENT_EXAMPLE = """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "FUNDING_044",
                "message": "자기 자신에게는 서프라이즈 펀딩을 열 수 없습니다.",
                "detail": null
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
        private const val SURPRISE_FUNDING_ALREADY_EXISTS_EXAMPLE = """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "FUNDING_045",
                "message": "이미 이 친구를 위한 공개 전 서프라이즈 펀딩이 있습니다.",
                "detail": null
              }
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
        private const val CREATE_FUNDING_REQUEST_EXAMPLE = """
            {
              "wishId": 3,
              "bank": "TOSS_BANK",
              "accountNumber": "100159014821",
              "targetAmount": 350000,
              "deadline": "2026-11-30",
              "story": "올해 생일에는 오래 사용한 이어폰을 바꾸고 싶어요.",
              "promise": "선물을 받으면 인증 사진을 올릴게요."
            }
        """
        private const val CREATE_FUNDING_RESPONSE_EXAMPLE = """
            {
              "success": true,
              "data": {
                "fundingId": 12,
                "shareKey": "0123456789abcdef0123456789abcdef",
                "type": "BIRTHDAY",
                "status": "FUNDING",
                "shareUrl": "https://botem.me/share/0123456789abcdef0123456789abcdef",
                "deadline": "2026-11-30T23:59:59.999999",
                "revealAt": null
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
                "detail": "마감일은 내일부터 선택할 수 있습니다."
              }
            }
        """
        private const val WISH_ID_INVALID_EXAMPLE = """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "COMMON_001",
                "message": "입력값이 올바르지 않습니다.",
                "detail": "wishId: 0보다 커야 합니다"
              }
            }
        """
        private const val WISH_NOT_FOUND_EXAMPLE = """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "WISH_002",
                "message": "위시를 찾을 수 없습니다.",
                "detail": null
              }
            }
        """
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
        private const val WISH_IN_FUNDING_EXAMPLE = """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "WISH_003",
                "message": "진행 중인 펀딩에 사용 중인 위시입니다.",
                "detail": null
              }
            }
        """
        private const val ACTIVE_FUNDING_LIMIT_EXCEEDED_EXAMPLE = """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "FUNDING_019",
                "message": "진행 중인 펀딩은 최대 5개까지 개설할 수 있습니다.",
                "detail": null
              }
            }
        """
        private const val DETAIL_EXAMPLE = """
            {
              "success": true,
              "data": {
                "fundingId": 12,
                "shareKey": "0123456789abcdef0123456789abcdef",
                "type": "BIRTHDAY",
                "title": null,
                "celebrantName": "지유",
                "celebrantProfileImageUrl": "https://image.example.com/profile/jiyu.jpg",
                "targetAmount": 300000,
                "product": {
                  "name": "에어팟 프로 3",
                  "imageUrl": "https://image.example.com/product.jpg",
                  "price": 300000,
                  "url": "https://www.coupang.com/vp/products/1",
                  "sellerName": "BOTEM 브랜드샵"
                },
                "productChanged": false,
                "productChangedAt": null,
                "story": "올해 생일에는 오래 사용한 이어폰을 바꾸고 싶어요.",
                "promise": "선물을 받으면 인증 사진을 올릴게요.",
                "deadline": "2026-11-30T23:59:59",
                "extended": false,
                "remainingDays": 12,
                "confirmedPercentage": 40,
                "pendingPercentage": 10,
                "participantCount": 2,
                "participants": [
                  { "order": 1, "name": "지유", "profileImageUrl": "https://image.example.com/profile.jpg", "contributedDate": "2026-11-12" },
                  { "order": 2, "name": "익명 친구", "profileImageUrl": null, "contributedDate": "2026-11-13" }
                ],
                "displayStatus": "IN_PROGRESS",
                "canContributeAmount": true,
                "canParticipate": true,
                "ownerPreview": false,
                "giftVerification": null
              },
              "error": null
            }
        """
        private const val EXPIRED_TOKEN_EXAMPLE = """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "AUTH_003",
                "message": "만료된 토큰입니다.",
                "detail": null
              }
            }
        """
        private const val MANAGEMENT_DETAIL_EXAMPLE = """
            {
              "success": true,
              "data": {
                "fundingId": 12,
                "shareKey": "0123456789abcdef0123456789abcdef",
                "type": "BIRTHDAY",
                "title": "지유의 생일",
                "story": "올해 생일에는 오래 사용한 헤드폰을 바꾸고 싶어요.",
                "promise": null,
                "product": {
                  "name": "소니 WH-1000XM5",
                  "imageUrl": "https://image.example.com/product.jpg",
                  "price": 459000,
                  "url": "https://shop.example.com/product",
                  "sellerName": "BOTEM 브랜드샵"
                },
                "productChanged": false,
                "productChangedAt": null,
                "targetAmount": 459000,
                "deadline": "2026-11-30T23:59:59.999999",
                "revealAt": null,
                "recipientWithdrawn": false,
                "remainingDays": 7,
                "confirmedPercentage": 45,
                "pendingPercentage": 15,
                "pendingContributionCount": 2,
                "targetReached": false,
                "extended": false,
                "earlyClosedAt": null,
                "giftPreparedAt": null,
                "participantCount": 3,
                "participants": [
                  {
                    "contributionId": 31,
                    "order": 1,
                    "name": "하린",
                    "profileImageUrl": "https://image.example.com/profile.jpg",
                    "status": "CONFIRMED",
                    "participatedAt": "2026-11-20T14:03:12",
                    "thankCardSent": true,
                    "thankCardSentAt": "2026-11-24T18:30:00"
                  },
                  {
                    "contributionId": 32,
                    "order": 2,
                    "name": "도윤",
                    "profileImageUrl": null,
                    "status": "PENDING_CONFIRMATION",
                    "participatedAt": "2026-11-21T09:41:00",
                    "thankCardSent": false,
                    "thankCardSentAt": null
                  },
                  {
                    "contributionId": 33,
                    "order": 3,
                    "name": "민지",
                    "profileImageUrl": null,
                    "status": "MESSAGE_ONLY",
                    "participatedAt": "2026-11-22T20:15:30",
                    "thankCardSent": false,
                    "thankCardSentAt": null
                  }
                ],
                "messageCount": 3,
                "managementState": "IN_PROGRESS",
                "actions": {
                  "canCloseEarly": true,
                  "canExtend": false,
                  "canChangeProduct": true,
                  "canReissueShareCard": true
                }
              },
              "error": null
            }
        """
        private const val EARLY_CLOSE_RESPONSE_EXAMPLE = """
            {
              "success": true,
              "data": {
                "fundingId": 12,
                "type": "BIRTHDAY",
                "status": "CLOSED",
                "deadline": "2026-11-30T23:59:59.999999",
                "revealAt": null,
                "earlyClosedAt": "2026-11-18T15:30:00"
              },
              "error": null
            }
        """
        private const val CHANGE_PRODUCT_REQUEST_EXAMPLE = """
            {
              "wishId": 7
            }
        """
        private const val CHANGE_PRODUCT_RESPONSE_EXAMPLE = """
            {
              "success": true,
              "data": {
                "fundingId": 12,
                "product": {
                  "name": "소니 WH-1000XM6",
                  "imageUrl": "https://image.example.com/new-product.jpg",
                  "price": 519000,
                  "url": "https://shop.example.com/new-product",
                  "sellerName": "BOTEM 브랜드샵"
                },
                "productChangedAt": "2026-11-18T15:40:00"
              },
              "error": null
            }
        """
        private const val EXTEND_FUNDING_REQUEST_EXAMPLE = """
            {
              "newDeadline": "2026-11-30"
            }
        """
        private const val EXTEND_FUNDING_RESPONSE_EXAMPLE = """
            {
              "success": true,
              "data": {
                "fundingId": 12,
                "status": "FUNDING",
                "deadline": "2026-11-30T23:59:59.999999",
                "extensionCount": 1,
                "extendedAt": "2026-11-18T14:30:00"
              },
              "error": null
            }
        """
        private const val FUNDING_EXTENSION_DATE_FORMAT_INVALID_EXAMPLE = """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "COMMON_001",
                "message": "입력값이 올바르지 않습니다.",
                "detail": "newDeadline: 올바르지 않은 값입니다"
              }
            }
        """
        private const val RESULT_EXAMPLE = """
            {
              "success": true,
              "data": {
                "fundingId": 12,
                "productName": "다이슨 에어랩",
                "productImageUrl": "https://image.example.com/product.jpg",
                "productUrl": "https://www.coupang.com/vp/products/1",
                "productChanged": false,
                "targetAmount": 699000,
                "confirmedAmount": 580000,
                "achievementPercentage": 82,
                "targetReached": false,
                "participantCount": 8,
                "messageCount": 7,
                "pendingContributionCount": 2,
                "pendingCleanupRequired": true,
                "resultOpenedAt": null,
                "canExtend": false,
                "giftChosen": false,
                "contributions": [
                  {
                    "contributionId": 31,
                    "name": "서준",
                    "profileImageUrl": "https://image.example.com/profile.jpg",
                    "status": "CONFIRMED",
                    "participatedAt": "2026-11-10T14:20:00",
                    "hasMessage": true,
                    "hasPhoto": true,
                    "hasVoice": false,
                    "thankCardSent": false
                  },
                  {
                    "contributionId": 32,
                    "name": "서아",
                    "profileImageUrl": null,
                    "status": "PENDING_CONFIRMATION",
                    "participatedAt": "2026-11-12T09:05:00",
                    "hasMessage": false,
                    "hasPhoto": false,
                    "hasVoice": false,
                    "thankCardSent": false
                  }
                ]
              },
              "error": null
            }
        """
        private const val OPENED_RESULT_EXAMPLE = """
            {
              "success": true,
              "data": {
                "fundingId": 12,
                "productName": "다이슨 에어랩",
                "productImageUrl": "https://image.example.com/product.jpg",
                "productUrl": "https://www.coupang.com/vp/products/1",
                "productChanged": false,
                "targetAmount": 699000,
                "confirmedAmount": 710000,
                "achievementPercentage": 101,
                "targetReached": true,
                "participantCount": 8,
                "messageCount": 7,
                "pendingContributionCount": 0,
                "pendingCleanupRequired": false,
                "resultOpenedAt": "2026-11-15T00:00:00",
                "canExtend": false,
                "giftChosen": false,
                "contributions": [
                  {
                    "contributionId": 31,
                    "name": "서준",
                    "profileImageUrl": "https://image.example.com/profile.jpg",
                    "status": "CONFIRMED",
                    "participatedAt": "2026-11-10T14:20:00",
                    "hasMessage": true,
                    "hasPhoto": true,
                    "hasVoice": false,
                    "thankCardSent": true
                  }
                ]
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
        private const val FUNDING_NOT_ENDED_EXAMPLE = """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "FUNDING_005",
                "message": "펀딩이 마감된 후 결과를 확인할 수 있습니다.",
                "detail": null
              }
            }
        """
        private const val FUNDING_RESULT_NOT_OPENABLE_EXAMPLE = """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "FUNDING_006",
                "message": "현재 상태에서는 펀딩 결과를 열 수 없습니다.",
                "detail": null
              }
            }
        """
        private const val PENDING_CONTRIBUTION_EXISTS_EXAMPLE = """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "FUNDING_007",
                "message": "입금 확인 대기 중인 참여를 모두 확인한 후 결과를 열 수 있습니다.",
                "detail": null
              }
            }
        """
        private const val FUNDING_TARGET_REACHED_EXAMPLE = """
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
        private const val FUNDING_EXTENSION_BEFORE_DEADLINE_EXAMPLE = """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "FUNDING_037",
                "message": "펀딩이 마감된 후 기간을 연장할 수 있습니다.",
                "detail": null
              }
            }
        """
        private const val FUNDING_EXTENSION_NOT_AVAILABLE_EXAMPLE = """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "FUNDING_038",
                "message": "현재 상태에서는 펀딩 기간을 연장할 수 없습니다.",
                "detail": null
              }
            }
        """
        private const val FUNDING_EXTENSION_LIMIT_EXCEEDED_EXAMPLE = """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "FUNDING_039",
                "message": "펀딩 기간은 한 번만 연장할 수 있습니다.",
                "detail": null
              }
            }
        """
        private const val FUNDING_EXTENSION_DEADLINE_INVALID_EXAMPLE = """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "FUNDING_040",
                "message": "새 마감일은 현재 시각과 기존 마감일 이후여야 합니다.",
                "detail": null
              }
            }
        """
        private const val FUNDING_EXTENSION_PERIOD_EXCEEDED_EXAMPLE = """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "FUNDING_041",
                "message": "펀딩 기간은 최대 14일까지 연장할 수 있습니다.",
                "detail": null
              }
            }
        """
        private const val FUNDING_EARLY_CLOSE_NOT_AVAILABLE_EXAMPLE = """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "FUNDING_042",
                "message": "현재 상태에서는 펀딩을 조기 마감할 수 없습니다.",
                "detail": null
              }
            }
        """
        private const val GIFT_CHOICE_EXAMPLE = """
            {
              "success": true,
              "data": {
                "fundingId": 12,
                "giftVerificationId": 7,
                "productName": "다이슨 에어랩",
                "productImageUrl": "https://image.example.com/product.jpg",
                "productUrl": "https://www.coupang.com/vp/products/1"
              },
              "error": null
            }
        """
        private const val GIFT_CHOICE_NOT_AVAILABLE_EXAMPLE = """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "FUNDING_059",
                "message": "펀딩 결과를 연 뒤 구매 전까지만 선물을 고를 수 있습니다.",
                "detail": null
              }
            }
        """
        private const val FUNDING_PRODUCT_CHANGE_NOT_AVAILABLE_EXAMPLE = """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "FUNDING_043",
                "message": "현재 상태에서는 펀딩 상품을 변경할 수 없습니다.",
                "detail": null
              }
            }
        """
        private const val SURPRISE_GIFT_PREPARATION_EXAMPLE = """
            {
              "success": true,
              "data": {
                "fundingId": 21,
                "shareKey": "0123456789abcdef0123456789abcdef",
                "recipientName": "지유",
                "recipientWithdrawn": false,
                "recipientPayoutAccount": {
                  "bank": "KAKAO_BANK",
                  "bankName": "카카오뱅크",
                  "accountNumber": "3333012345678",
                  "accountHolder": "김지유"
                },
                "productName": "소니 WH-1000XM5",
                "productImageUrl": "https://image.example.com/product.jpg",
                "productUrl": "https://shop.example.com/product",
                "productSellerName": "BOTEM 브랜드샵",
                "targetAmount": 459000,
                "confirmedAmount": 420000,
                "achievementPercentage": 91,
                "targetReached": false,
                "participantCount": 7,
                "messageCount": 6,
                "pendingContributionCount": 1,
                "pendingCleanupRequired": true,
                "fundingStartedAt": "2026-10-31T10:00:00",
                "deadline": "2026-11-15T23:59:59.999999",
                "earlyClosedAt": null,
                "revealAt": "2026-11-17T00:00:00",
                "giftPreparedAt": null
              },
              "error": null
            }
        """
        private const val SURPRISE_GIFT_PREPARED_EXAMPLE = """
            {
              "success": true,
              "data": {
                "fundingId": 21,
                "shareKey": "0123456789abcdef0123456789abcdef",
                "recipientName": "지유",
                "recipientWithdrawn": false,
                "recipientPayoutAccount": {
                  "bank": "KAKAO_BANK",
                  "bankName": "카카오뱅크",
                  "accountNumber": "3333012345678",
                  "accountHolder": "김지유"
                },
                "productName": "소니 WH-1000XM5",
                "productImageUrl": "https://image.example.com/product.jpg",
                "productUrl": "https://shop.example.com/product",
                "productSellerName": "BOTEM 브랜드샵",
                "targetAmount": 459000,
                "confirmedAmount": 420000,
                "achievementPercentage": 91,
                "targetReached": false,
                "participantCount": 7,
                "messageCount": 6,
                "pendingContributionCount": 0,
                "pendingCleanupRequired": false,
                "fundingStartedAt": "2026-10-31T10:00:00",
                "deadline": "2026-11-15T23:59:59.999999",
                "earlyClosedAt": null,
                "revealAt": "2026-11-17T00:00:00",
                "giftPreparedAt": "2026-11-16T10:30:00"
              },
              "error": null
            }
        """
        private const val SURPRISE_FUNDING_REQUIRED_EXAMPLE = """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "FUNDING_014",
                "message": "서프라이즈 펀딩에서만 사용할 수 있습니다.",
                "detail": null
              }
            }
        """
        private const val SURPRISE_GIFT_PREPARATION_NOT_AVAILABLE_EXAMPLE = """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "FUNDING_047",
                "message": "현재 상태에서는 서프라이즈 선물 준비를 진행할 수 없습니다.",
                "detail": null
              }
            }
        """
    }
}
