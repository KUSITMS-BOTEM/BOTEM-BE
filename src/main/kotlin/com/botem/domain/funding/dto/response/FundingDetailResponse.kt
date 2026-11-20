package com.botem.domain.funding.dto.response

import com.botem.domain.funding.entity.FundingProductSnapshot
import com.botem.domain.funding.entity.FundingType
import com.botem.domain.funding.entity.GiftVerification
import java.time.LocalDate
import java.time.LocalDateTime

// 공유 링크로 보는 펀딩 상세. 모인 금액은 다른 참여자에게 노출하지 않도록 달성률만 반환하고, 목표 금액은 보여준다
data class FundingDetailResponse(
    val fundingId: Long,
    val shareKey: String,
    val type: FundingType,
    val title: String?,
    // '{이름}의 생일' 제목용 주인공. 생일 펀딩은 개설자, 서프라이즈는 받는 사람이다
    val celebrantName: String,
    val celebrantProfileImageUrl: String?,
    val targetAmount: Long,
    val product: FundingDetailProductResponse,
    val productChanged: Boolean,
    val productChangedAt: LocalDateTime?,
    val story: String?,
    val promise: String?,
    val deadline: LocalDateTime,
    val extended: Boolean,
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

data class FundingDetailProductResponse(
    val name: String,
    val imageUrl: String,
    val price: Long,
    val url: String,
    val sellerName: String?,
) {
    companion object {
        fun from(product: FundingProductSnapshot): FundingDetailProductResponse =
            FundingDetailProductResponse(
                name = product.name,
                imageUrl = product.imageUrl,
                price = product.price,
                url = product.url,
                sellerName = product.sellerName,
            )
    }
}

// order는 첫 참여 순서(1부터). 역할 배지 문구는 프론트가 순서로 결정한다
data class FundingDetailParticipantResponse(
    val order: Int,
    val name: String,
    val profileImageUrl: String?,
    val contributedDate: LocalDate,
)

enum class FundingDisplayStatus {
    IN_PROGRESS,
    TARGET_REACHED,
    CLOSED,
    GIFT_PREPARING,
    COMPLETED,
}
