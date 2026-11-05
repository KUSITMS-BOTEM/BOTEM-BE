package com.botem.domain.account.dto.response

import com.botem.domain.account.entity.Bank

data class BankResponse(
    val code: Bank,
    val name: String,
) {
    companion object {
        fun from(bank: Bank): BankResponse =
            BankResponse(
                code = bank,
                name = bank.displayName,
            )
    }
}
