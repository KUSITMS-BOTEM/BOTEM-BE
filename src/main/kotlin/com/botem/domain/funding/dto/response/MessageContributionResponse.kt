package com.botem.domain.funding.dto.response

import com.botem.domain.funding.entity.Contribution
import com.botem.domain.funding.entity.ContributionStatus

data class MessageContributionResponse(
    val contributionId: Long,
    val participantName: String,
    val status: ContributionStatus,
) {
    companion object {
        fun from(contribution: Contribution): MessageContributionResponse =
            MessageContributionResponse(
                contributionId = contribution.id,
                participantName = contribution.participantName,
                status = contribution.status,
            )
    }
}
