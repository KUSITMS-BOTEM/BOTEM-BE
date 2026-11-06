package com.botem.domain.account.controller

import com.botem.domain.account.controller.docs.PayoutAccountControllerDocs
import com.botem.domain.account.dto.request.SavePayoutAccountRequest
import com.botem.domain.account.dto.response.BankResponse
import com.botem.domain.account.dto.response.PayoutAccountResponse
import com.botem.domain.account.service.PayoutAccountService
import com.botem.global.response.ApiResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/users/me/payout-accounts")
class PayoutAccountController(
    private val payoutAccountService: PayoutAccountService,
) : PayoutAccountControllerDocs {

    @GetMapping
    override fun getPayoutAccounts(
        @AuthenticationPrincipal userId: Long,
    ): ApiResponse<List<PayoutAccountResponse>> =
        ApiResponse.success(payoutAccountService.getPayoutAccounts(userId))

    @GetMapping("/banks")
    override fun getSupportedBanks(): ApiResponse<List<BankResponse>> =
        ApiResponse.success(payoutAccountService.getSupportedBanks())

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    override fun createPayoutAccount(
        @AuthenticationPrincipal userId: Long,
        @Valid @RequestBody request: SavePayoutAccountRequest,
    ): ApiResponse<PayoutAccountResponse> =
        ApiResponse.success(payoutAccountService.createPayoutAccount(userId, request))

    @PutMapping("/{accountId}")
    override fun updatePayoutAccount(
        @AuthenticationPrincipal userId: Long,
        @PathVariable accountId: Long,
        @Valid @RequestBody request: SavePayoutAccountRequest,
    ): ApiResponse<PayoutAccountResponse> =
        ApiResponse.success(payoutAccountService.updatePayoutAccount(userId, accountId, request))

    @PatchMapping("/{accountId}/default")
    override fun designateDefaultAccount(
        @AuthenticationPrincipal userId: Long,
        @PathVariable accountId: Long,
    ): ApiResponse<PayoutAccountResponse> =
        ApiResponse.success(payoutAccountService.designateDefaultAccount(userId, accountId))
}
