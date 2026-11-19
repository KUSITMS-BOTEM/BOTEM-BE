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
    val paperTemplateCode: String?,
    val decorationCode: String?,
    val photoUrl: String?,
    val voiceUrl: String?,
    val voiceDurationSeconds: Int?,
    val accessToken: String,
    val accessTokenExpiresAt: Instant,
) {
    companion object {
        fun of(
            contribution: Contribution,
            rollingPaperMessage: RollingPaperMessage?,
            accessToken: String,
            accessTokenExpiresAt: Instant,
        ): GuestContributionResponse =
            GuestContributionResponse(
                contributionId = contribution.id,
                participantName = contribution.participantName,
                amount = contribution.amount,
                status = contribution.status,
                message = rollingPaperMessage?.content,
                paperTemplateCode = rollingPaperMessage?.paperTemplateCode,
                decorationCode = rollingPaperMessage?.decorationCode,
                photoUrl = rollingPaperMessage?.photoUrl,
                voiceUrl = rollingPaperMessage?.voiceUrl,
                voiceDurationSeconds = rollingPaperMessage?.voiceDurationSeconds,
                accessToken = accessToken,
                accessTokenExpiresAt = accessTokenExpiresAt,
            )
    }
}

data class UpdatedGuestContributionResponse(
    val contributionId: Long,
    val participantName: String,
    val amount: Long,
    val status: ContributionStatus,
    val message: String?,
    val paperTemplateCode: String,
    val decorationCode: String?,
    val photoUrl: String?,
    val voiceUrl: String?,
    val voiceDurationSeconds: Int?,
) {
    companion object {
        fun of(
            contribution: Contribution,
            rollingPaperMessage: RollingPaperMessage,
        ): UpdatedGuestContributionResponse =
            UpdatedGuestContributionResponse(
                contributionId = contribution.id,
                participantName = contribution.participantName,
                amount = contribution.amount,
                status = contribution.status,
                message = rollingPaperMessage.content,
                paperTemplateCode = rollingPaperMessage.paperTemplateCode,
                decorationCode = rollingPaperMessage.decorationCode,
                photoUrl = rollingPaperMessage.photoUrl,
                voiceUrl = rollingPaperMessage.voiceUrl,
                voiceDurationSeconds = rollingPaperMessage.voiceDurationSeconds,
            )
    }
}
