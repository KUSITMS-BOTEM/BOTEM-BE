package com.botem.domain.funding.dto.response

import com.botem.domain.funding.entity.Contribution
import com.botem.domain.funding.entity.ContributionStatus
import com.botem.domain.funding.entity.RollingPaperMessage
import java.time.Instant

data class GuestContributionResponse(
    val contributionId: Long,
    val participantName: String,
    val amount: Long,
    val status: ContributionStatus,
    val message: String?,
    val paperTemplateCode: String,
    val accessToken: String,
    val accessTokenExpiresAt: Instant,
) {
    companion object {
        fun of(
            contribution: Contribution,
            rollingPaperMessage: RollingPaperMessage,
            accessToken: String,
            accessTokenExpiresAt: Instant,
        ): GuestContributionResponse =
            GuestContributionResponse(
                contributionId = contribution.id,
                participantName = contribution.participantName,
                amount = contribution.amount,
                status = contribution.status,
                message = rollingPaperMessage.content,
                paperTemplateCode = rollingPaperMessage.paperTemplateCode,
                accessToken = accessToken,
                accessTokenExpiresAt = accessTokenExpiresAt,
            )
    }
}
