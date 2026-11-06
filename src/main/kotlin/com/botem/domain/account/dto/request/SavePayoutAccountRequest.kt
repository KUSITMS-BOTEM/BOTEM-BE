package com.botem.domain.account.dto.request

import com.botem.domain.account.entity.Bank
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class SavePayoutAccountRequest(
    val bank: Bank,

    @field:NotBlank
    @field:Size(max = 30)
    val accountNumber: String,

    val accountHolder: String? = null,
)
