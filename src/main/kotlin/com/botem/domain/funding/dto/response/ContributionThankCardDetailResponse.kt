package com.botem.domain.funding.dto.response

import com.botem.domain.funding.entity.Contribution
import com.botem.domain.funding.entity.ContributionThankCard
import com.botem.domain.funding.entity.Funding
import com.botem.domain.funding.entity.FundingType
import com.botem.domain.user.entity.User
import com.botem.domain.funding.entity.ThankCardColor
import java.time.LocalDateTime

data class ContributionThankCardDetailResponse(
    val thankCardId: Long,
    val fundingId: Long,
    val contributionId: Long,
    val senderName: String,
    val senderRole: ContributionThankCardSenderRole,
    val fundingType: FundingType,
    val recipientName: String,
    val productName: String,
    val productImageUrl: String,
    val message: String,
    val paperColor: ThankCardColor,
    val sentAt: LocalDateTime,
    val shareUrl: String,
) {
    companion object {
        fun of(
            thankCard: ContributionThankCard,
            contribution: Contribution,
            funding: Funding,
            sender: User,
            shareUrl: String,
        ): ContributionThankCardDetailResponse =
            ContributionThankCardDetailResponse(
                thankCardId = thankCard.id,
                fundingId = funding.id,
                contributionId = contribution.id,
                senderName = sender.name ?: sender.nickname,
                senderRole = if (funding.type == FundingType.SURPRISE && funding.recipientId == thankCard.senderUserId) {
                    ContributionThankCardSenderRole.SURPRISE_RECIPIENT
                } else {
                    ContributionThankCardSenderRole.FUNDING_OWNER
                },
                fundingType = funding.type,
                recipientName = contribution.participantName,
                productName = funding.product.name,
                productImageUrl = funding.product.imageUrl,
                message = thankCard.message,
                paperColor = thankCard.paperColor,
                sentAt = thankCard.sentAt,
                shareUrl = shareUrl,
            )
    }
}

enum class ContributionThankCardSenderRole {
    FUNDING_OWNER,
    SURPRISE_RECIPIENT,
}
