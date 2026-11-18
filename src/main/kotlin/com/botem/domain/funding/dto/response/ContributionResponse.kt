package com.botem.domain.funding.dto.response

import com.botem.domain.account.entity.Bank
import com.botem.domain.funding.entity.Contribution
import com.botem.domain.funding.entity.ContributionStatus

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
)

data class TransferSentResponse(
    val contributionId: Long,
    val status: ContributionStatus,
)
