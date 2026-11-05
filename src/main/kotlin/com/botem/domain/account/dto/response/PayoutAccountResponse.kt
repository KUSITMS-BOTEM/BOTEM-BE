package com.botem.domain.account.dto.response

import com.botem.domain.account.entity.AccountVerificationStatus
import com.botem.domain.account.entity.Bank
import com.botem.domain.account.entity.PayoutAccount

data class PayoutAccountResponse(
    val id: Long,
    val bank: Bank,
    val bankName: String,
    val accountNumber: String,
    val accountHolder: String?,
    val isDefault: Boolean,
    val verificationStatus: AccountVerificationStatus,
) {
    companion object {
        fun from(account: PayoutAccount, accountNumber: String): PayoutAccountResponse =
            PayoutAccountResponse(
                id = account.id,
                bank = account.bank,
                bankName = account.bank.displayName,
                accountNumber = accountNumber,
                accountHolder = account.accountHolder,
                isDefault = account.isDefault,
                verificationStatus = account.verificationStatus,
            )
    }
}
