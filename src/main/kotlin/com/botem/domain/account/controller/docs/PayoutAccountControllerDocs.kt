package com.botem.domain.account.controller.docs

import com.botem.domain.account.dto.request.SavePayoutAccountRequest
import com.botem.domain.account.dto.response.BankResponse
import com.botem.domain.account.dto.response.PayoutAccountResponse
import com.botem.global.response.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Payout Account", description = "받을 계좌 API")
interface PayoutAccountControllerDocs {

    @Operation(summary = "받을 계좌 목록 조회", description = "로그인한 사용자가 등록한 받을 계좌를 조회합니다.")
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "조회 성공"),
        SwaggerApiResponse(responseCode = "401", description = "인증 실패"),
    )
    fun getPayoutAccounts(
        @Parameter(hidden = true) userId: Long,
    ): ApiResponse<List<PayoutAccountResponse>>

    @Operation(summary = "지원 은행 목록 조회", description = "받을 계좌에 등록할 수 있는 은행 코드와 이름을 조회합니다.")
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "조회 성공"),
        SwaggerApiResponse(responseCode = "401", description = "인증 실패"),
    )
    fun getSupportedBanks(): ApiResponse<List<BankResponse>>

    @Operation(
        summary = "받을 계좌 등록",
        description = "받을 계좌를 등록합니다. 첫 계좌는 기본 계좌로 지정되며 외부 확인 서비스가 없어 확인 전 상태로 저장됩니다.",
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "201", description = "등록 성공"),
        SwaggerApiResponse(responseCode = "400", description = "계좌 개수 또는 입력 형식 오류"),
        SwaggerApiResponse(responseCode = "401", description = "인증 실패"),
        SwaggerApiResponse(responseCode = "409", description = "동일 은행 중복"),
    )
    fun createPayoutAccount(
        @Parameter(hidden = true) userId: Long,
        request: SavePayoutAccountRequest,
    ): ApiResponse<PayoutAccountResponse>

    @Operation(
        summary = "받을 계좌 수정",
        description = "본인의 받을 계좌를 수정합니다. 수정 후 계좌 확인 상태는 확인 전으로 초기화됩니다.",
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "수정 성공"),
        SwaggerApiResponse(responseCode = "400", description = "입력 형식 오류"),
        SwaggerApiResponse(responseCode = "401", description = "인증 실패"),
        SwaggerApiResponse(responseCode = "404", description = "계좌 없음"),
        SwaggerApiResponse(responseCode = "409", description = "동일 은행 중복"),
    )
    fun updatePayoutAccount(
        @Parameter(hidden = true) userId: Long,
        @Parameter(description = "받을 계좌 ID") accountId: Long,
        request: SavePayoutAccountRequest,
    ): ApiResponse<PayoutAccountResponse>

    @Operation(summary = "기본 계좌 지정", description = "본인의 계좌를 기본으로 지정하고 기존 기본 계좌를 해제합니다.")
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "지정 성공"),
        SwaggerApiResponse(responseCode = "401", description = "인증 실패"),
        SwaggerApiResponse(responseCode = "404", description = "계좌 없음"),
    )
    fun designateDefaultAccount(
        @Parameter(hidden = true) userId: Long,
        @Parameter(description = "받을 계좌 ID") accountId: Long,
    ): ApiResponse<PayoutAccountResponse>
}
