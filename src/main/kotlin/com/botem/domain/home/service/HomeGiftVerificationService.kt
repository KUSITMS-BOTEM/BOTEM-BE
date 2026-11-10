package com.botem.domain.home.service

import com.botem.domain.funding.entity.ContributionStatus
import com.botem.domain.funding.entity.GiftVerificationStatus
import com.botem.domain.funding.repository.GiftVerificationRepository
import com.botem.domain.home.dto.response.GiftVerificationBannerResponse
import com.botem.domain.home.dto.response.GiftVerificationTargetListResponse
import com.botem.domain.home.dto.response.GiftVerificationTargetResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class HomeGiftVerificationService(
    private val giftVerificationRepository: GiftVerificationRepository,
) {

    @Transactional(readOnly = true)
    fun getBanner(userId: Long): GiftVerificationBannerResponse {
        val pendingCount = giftVerificationRepository.countByRecipientAndStatus(
            userId = userId,
            status = GiftVerificationStatus.PENDING,
        )

        return GiftVerificationBannerResponse.from(pendingCount)
    }

    @Transactional(readOnly = true)
    fun getTargets(userId: Long): GiftVerificationTargetListResponse {
        val targets = giftVerificationRepository.findTargetsByRecipientAndStatus(
            userId = userId,
            status = GiftVerificationStatus.PENDING,
            excludedContributionStatus = ContributionStatus.CANCELLED,
        ).map(GiftVerificationTargetResponse::from)

        return GiftVerificationTargetListResponse(
            pendingCount = targets.size,
            targets = targets,
        )
    }
}
