package com.botem.domain.funding.service

import com.botem.domain.funding.entity.FundingStatus
import com.botem.domain.funding.repository.FundingRepository
import com.botem.domain.user.service.ActiveFundingChecker
import org.springframework.stereotype.Component

@Component
class FundingActiveFundingChecker(
    private val fundingRepository: FundingRepository,
) : ActiveFundingChecker {

    override fun existsByUserId(userId: Long): Boolean =
        fundingRepository.existsByOwnerIdAndStatusNotIn(userId, ENDED_STATUSES)

    companion object {
        private val ENDED_STATUSES = setOf(
            FundingStatus.COMPLETED,
            FundingStatus.CANCELLED,
        )
    }
}
