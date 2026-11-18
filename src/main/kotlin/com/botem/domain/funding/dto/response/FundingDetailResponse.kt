package com.botem.domain.funding.dto.response

import com.botem.domain.funding.entity.FundingProductSnapshot
import com.botem.domain.funding.entity.FundingType
import com.botem.domain.funding.entity.GiftVerification
import com.botem.domain.funding.entity.SurpriseThankCard
import java.time.LocalDateTime

// 공유 링크로 보는 펀딩 상세. 금액은 다른 참여자에게 노출하지 않도록 달성률만 반환한다
data class FundingDetailResponse(
    val fundingId: Long,
    val type: FundingType,
    val title: String?,
    val product: FundingDetailProductResponse,
    val story: String?,
    val promise: String?,
    val deadline: LocalDateTime,
    val remainingDays: Long?,
    val confirmedPercentage: Int,
    val pendingPercentage: Int,
    val participantCount: Int,
    val participants: List<FundingDetailParticipantResponse>,
    val displayStatus: FundingDisplayStatus,
    val canContributeAmount: Boolean,
    val canParticipate: Boolean,
    val ownerPreview: Boolean,
    val giftVerification: FundingDetailGiftVerificationResponse?,
    val thankCard: FundingDetailThankCardResponse?,
)

// 구매 경로와 대체 상품명은 공개하지 않는다
data class FundingDetailGiftVerificationResponse(
    val imageUrl: String?,
    val thankYouMessage: String?,
    val completedAt: LocalDateTime?,
) {
    companion object {
        fun from(giftVerification: GiftVerification): FundingDetailGiftVerificationResponse =
            FundingDetailGiftVerificationResponse(
                imageUrl = giftVerification.imageUrl,
                thankYouMessage = giftVerification.thankYouMessage,
                completedAt = giftVerification.completedAt,
            )
    }
}

data class FundingDetailThankCardResponse(
    val message: String,
    val sentAt: LocalDateTime,
) {
    companion object {
        fun from(thankCard: SurpriseThankCard): FundingDetailThankCardResponse =
            FundingDetailThankCardResponse(
                message = thankCard.message,
                sentAt = thankCard.sentAt,
            )
    }
}

data class FundingDetailProductResponse(
    val name: String,
    val imageUrl: String,
    val price: Long,
    val url: String,
) {
    companion object {
        fun from(product: FundingProductSnapshot): FundingDetailProductResponse =
            FundingDetailProductResponse(
                name = product.name,
                imageUrl = product.imageUrl,
                price = product.price,
                url = product.url,
            )
    }
}

// order는 첫 참여 순서(1부터). 역할 배지 문구는 프론트가 순서로 결정한다
data class FundingDetailParticipantResponse(
    val order: Int,
    val name: String,
    val profileImageUrl: String?,
)

enum class FundingDisplayStatus {
    IN_PROGRESS,
    TARGET_REACHED,
    CLOSED,
    GIFT_PREPARING,
    COMPLETED,
}
