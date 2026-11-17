package com.botem.domain.funding.dto.response

import com.botem.domain.funding.entity.FundingProductSnapshot
import com.botem.domain.funding.entity.FundingType
import java.time.LocalDateTime

// 공유 링크로 보는 펀딩 상세. 금액은 다른 참여자에게 노출하지 않도록 달성률만 반환한다
data class FundingDetailResponse(
    val fundingId: Long,
    val type: FundingType,
    val title: String,
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
)

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
