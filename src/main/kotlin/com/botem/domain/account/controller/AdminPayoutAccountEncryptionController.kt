package com.botem.domain.account.controller

import com.botem.domain.account.controller.docs.AdminPayoutAccountEncryptionControllerDocs
import com.botem.domain.account.dto.request.RotatePayoutAccountEncryptionRequest
import com.botem.domain.account.dto.response.PayoutAccountEncryptionRotationResponse
import com.botem.domain.account.service.PayoutAccountEncryptionRotationService
import com.botem.global.response.ApiResponse
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin/payout-account-encryption")
class AdminPayoutAccountEncryptionController(
    private val payoutAccountEncryptionRotationService: PayoutAccountEncryptionRotationService,
) : AdminPayoutAccountEncryptionControllerDocs {

    @PostMapping("/rotate")
    override fun rotate(
        @Valid @RequestBody request: RotatePayoutAccountEncryptionRequest,
    ): ApiResponse<PayoutAccountEncryptionRotationResponse> =
        ApiResponse.success(
            payoutAccountEncryptionRotationService.rotate(
                afterAccountId = request.afterAccountId,
                batchSize = request.batchSize,
            ),
        )
}
