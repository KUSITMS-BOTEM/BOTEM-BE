package com.botem.domain.funding.dto.response

import com.botem.domain.account.entity.Bank
import com.botem.domain.account.entity.PayoutAccount

data class SurpriseRecipientPayoutAccountResponse(
    val bank: Bank,
    val bankName: String,
    val accountNumber: String,
    val accountHolder: String?,
) {
    companion object {
        fun from(account: PayoutAccount, accountNumber: String): SurpriseRecipientPayoutAccountResponse =
            SurpriseRecipientPayoutAccountResponse(
                bank = account.bank,
                bankName = account.bank.displayName,
                accountNumber = accountNumber,
                accountHolder = account.accountHolder,
            )
    }
}
