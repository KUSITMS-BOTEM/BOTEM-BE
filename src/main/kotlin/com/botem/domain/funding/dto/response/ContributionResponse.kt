package com.botem.domain.funding.dto.response

import com.botem.domain.account.entity.Bank
import com.botem.domain.account.service.AccountNumberCipher
import com.botem.domain.funding.entity.Contribution
import com.botem.domain.funding.entity.ContributionStatus
import com.botem.domain.funding.entity.Funding

data class ContributionResponse(
    val contributionId: Long,
    val participantName: String,
    val amount: Long,
    val status: ContributionStatus,
    val payoutAccount: ContributionPayoutAccountResponse,
) {
    companion object {
        fun of(contribution: Contribution, payoutAccount: ContributionPayoutAccountResponse): ContributionResponse =
            ContributionResponse(
                contributionId = contribution.id,
                participantName = contribution.participantName,
                amount = contribution.amount,
                status = contribution.status,
                payoutAccount = payoutAccount,
            )
    }
}

data class ContributionPayoutAccountResponse(
    val bank: Bank,
    val bankName: String,
    val accountNumber: String,
    val accountHolder: String?,
) {
    companion object {
        fun of(funding: Funding, accountNumberCipher: AccountNumberCipher): ContributionPayoutAccountResponse =
            ContributionPayoutAccountResponse(
                bank = funding.payoutAccount.bank,
                bankName = funding.payoutAccount.bank.displayName,
                accountNumber = accountNumberCipher.decrypt(funding.payoutAccount.encryptedAccountNumber),
                accountHolder = funding.payoutAccount.accountHolder,
            )
    }
}

data class TransferSentResponse(
    val contributionId: Long,
    val status: ContributionStatus,
)
