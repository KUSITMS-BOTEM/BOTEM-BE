package com.botem.domain.funding.service

import com.botem.domain.funding.dto.response.ClosedFundingGiftVerificationStatus
import com.botem.domain.funding.dto.response.ClosedFundingListResponse
import com.botem.domain.funding.dto.response.ClosedFundingResponse
import com.botem.domain.funding.entity.Funding
import com.botem.domain.funding.entity.FundingStatus
import com.botem.domain.funding.entity.FundingType
import com.botem.domain.funding.repository.ContributionRepository
import com.botem.domain.funding.repository.FundingRepository
import com.botem.domain.funding.repository.projection.FundingContributionSummary
import com.botem.domain.user.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

@Service
class ClosedFundingService(
    private val fundingRepository: FundingRepository,
    private val contributionRepository: ContributionRepository,
    private val userRepository: UserRepository,
    private val clock: Clock,
) {

    // ponytail: 개인별 마감 펀딩은 많지 않아 페이징 없이 전부 반환한다. 목록이 길어지면 커서 페이징 추가
    @Transactional(readOnly = true)
    fun getClosedFundings(userId: Long): ClosedFundingListResponse {
        val fundings = fundingRepository.findClosedFundingsByOwnerId(userId, LocalDateTime.now(clock))
        if (fundings.isEmpty()) {
            return ClosedFundingListResponse(emptyList())
        }

        val summaries = contributionRepository.summarizeByFundingIds(fundings.map(Funding::id))
            .associateBy(FundingContributionSummary::fundingId)
        val recipientNames = findRecipientNames(fundings)

        return ClosedFundingListResponse(
            fundings.map { funding ->
                val summary = summaries[funding.id] ?: FundingContributionSummary.empty(funding.id)

                ClosedFundingResponse(
                    fundingId = funding.id,
                    shareKey = funding.shareKey,
                    type = funding.type,
                    productName = funding.product.name,
                    productImageUrl = funding.product.imageUrl,
                    deadline = funding.deadline,
                    targetReached = summary.confirmedAmount >= funding.targetAmount,
                    participantCount = summary.participantCount,
                    recipientName = funding.recipientId?.let(recipientNames::get),
                    giftVerificationStatus = giftVerificationStatusOf(funding),
                )
            },
        )
    }

    private fun findRecipientNames(fundings: List<Funding>): Map<Long, String> {
        val recipientIds = fundings.mapNotNull(Funding::recipientId).distinct()
        if (recipientIds.isEmpty()) {
            return emptyMap()
        }

        return userRepository.findAllById(recipientIds).associate { it.id to (it.name ?: it.nickname) }
    }

    // 선물을 받는 사람이 개설자 본인인 생일 펀딩만 인증 상태를 보여준다. 서프라이즈 인증은 받는 사람의 몫이다
    private fun giftVerificationStatusOf(funding: Funding): ClosedFundingGiftVerificationStatus? =
        when {
            funding.type != FundingType.BIRTHDAY -> null
            funding.status == FundingStatus.COMPLETED -> ClosedFundingGiftVerificationStatus.COMPLETED
            funding.resultOpenedAt != null -> ClosedFundingGiftVerificationStatus.REQUIRED
            else -> null
        }
}
