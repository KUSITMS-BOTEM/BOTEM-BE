package com.botem.domain.home.dto.response

import com.botem.domain.funding.entity.FundingType

data class HomeFundingCardResponse(
    val fundingId: Long,
    val cardType: HomeFundingCardType,
    val fundingType: FundingType,
    val productName: String,
    val productImageUrl: String,
    val targetAmount: Long,
    val participantCount: Long,
    val participantPreviews: List<HomeFundingParticipantPreviewResponse> = emptyList(),
    val remainingDays: Long?,
    val confirmedPercentage: Int,
    val pendingPercentage: Int,
    val pendingContributionCount: Long,
    // 서프라이즈 카드 제목용 받는 사람 이름(없으면 닉네임). 생일 펀딩은 null
    val recipientName: String? = null,
    // 마감 전이지만 목표를 달성하고 확인 대기가 없어 지금 결과를 열 수 있는 생일 펀딩
    val earlyOpenable: Boolean = false,
)
