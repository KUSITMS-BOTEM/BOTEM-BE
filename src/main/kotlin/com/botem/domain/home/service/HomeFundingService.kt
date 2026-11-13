package com.botem.domain.home.service

import com.botem.domain.funding.entity.Funding
import com.botem.domain.funding.entity.FundingStatus
import com.botem.domain.funding.entity.FundingType
import com.botem.domain.funding.repository.ContributionRepository
import com.botem.domain.funding.repository.FundingRepository
import com.botem.domain.funding.repository.projection.FundingContributionSummary
import com.botem.domain.home.dto.response.HomeFundingCardResponse
import com.botem.domain.home.dto.response.HomeFundingCardType
import com.botem.domain.home.dto.response.HomeFundingListResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

@Service
class HomeFundingService(
    private val fundingRepository: FundingRepository,
    private val contributionRepository: ContributionRepository,
    private val clock: Clock,
) {

    @Transactional(readOnly = true)
    fun getHomeFundings(userId: Long): HomeFundingListResponse {
        val now = LocalDateTime.now(clock)
        val fundings = fundingRepository.findAllByOwnerIdAndStatusIn(userId, HOME_CANDIDATE_STATUSES)
        val summaries = getContributionSummaries(fundings)
        val cards = fundings.mapNotNull { funding ->
            toCard(funding, summaries[funding.id] ?: FundingContributionSummary.empty(funding.id), now)
        }
        val activeFundingCount = cards.count { it.cardType == HomeFundingCardType.IN_PROGRESS }

        return HomeFundingListResponse(
            fundingCards = sortCards(cards, fundings),
            activeFundingCount = activeFundingCount,
            canOpenFunding = activeFundingCount < MAX_ACTIVE_FUNDING_COUNT,
        )
    }

    private fun getContributionSummaries(fundings: List<Funding>): Map<Long, FundingContributionSummary> {
        if (fundings.isEmpty()) {
            return emptyMap()
        }

        return contributionRepository.summarizeByFundingIds(fundings.map(Funding::id))
            .associateBy(FundingContributionSummary::fundingId)
    }

    private fun toCard(
        funding: Funding,
        summary: FundingContributionSummary,
        now: LocalDateTime,
    ): HomeFundingCardResponse? {
        val cardType = when {
            isClosedUnopened(funding, now) -> HomeFundingCardType.CLOSED_UNOPENED
            isInProgress(funding, summary, now) -> HomeFundingCardType.IN_PROGRESS
            else -> return null
        }
        val remainingDays = if (cardType == HomeFundingCardType.IN_PROGRESS) {
            ChronoUnit.DAYS.between(now.toLocalDate(), cardTargetAt(funding).toLocalDate())
        } else {
            null
        }

        return HomeFundingCardResponse(
            fundingId = funding.id,
            cardType = cardType,
            fundingType = funding.type,
            productName = funding.product.name,
            productImageUrl = funding.product.imageUrl,
            targetAmount = funding.targetAmount,
            participantCount = summary.participantCount,
            remainingDays = remainingDays,
            confirmedPercentage = percentage(summary.confirmedAmount, funding.targetAmount),
            pendingPercentage = percentage(summary.pendingAmount, funding.targetAmount),
            pendingContributionCount = summary.pendingCount,
        )
    }

    private fun isClosedUnopened(
        funding: Funding,
        now: LocalDateTime,
    ): Boolean =
        funding.type == FundingType.BIRTHDAY &&
            funding.status in BIRTHDAY_CLOSED_CANDIDATE_STATUSES &&
            !funding.deadline.isAfter(now) &&
            funding.resultOpenedAt == null

    private fun isInProgress(
        funding: Funding,
        summary: FundingContributionSummary,
        now: LocalDateTime,
    ): Boolean =
        when (funding.type) {
            FundingType.BIRTHDAY ->
                funding.deadline.isAfter(now) &&
                    (
                        funding.status == FundingStatus.FUNDING ||
                            funding.status == FundingStatus.CLOSED &&
                            summary.confirmedAmount >= funding.targetAmount
                    )

            FundingType.SURPRISE ->
                funding.revealAt?.isAfter(now) == true &&
                    funding.status in SURPRISE_ACTIVE_STATUSES
        }

    private fun sortCards(
        cards: List<HomeFundingCardResponse>,
        fundings: List<Funding>,
    ): List<HomeFundingCardResponse> {
        val fundingById = fundings.associateBy(Funding::id)

        return cards.sortedWith(
            compareBy<HomeFundingCardResponse> { it.cardType != HomeFundingCardType.CLOSED_UNOPENED }
                .thenBy { card ->
                    if (card.cardType == HomeFundingCardType.IN_PROGRESS) {
                        cardTargetAt(fundingById.getValue(card.fundingId))
                    } else {
                        LocalDateTime.MIN
                    }
                }
                .thenBy(HomeFundingCardResponse::fundingId),
        )
    }

    private fun cardTargetAt(funding: Funding): LocalDateTime =
        when (funding.type) {
            FundingType.BIRTHDAY -> funding.deadline
            FundingType.SURPRISE -> requireNotNull(funding.revealAt)
        }

    private fun percentage(amount: Long, targetAmount: Long): Int {
        if (amount <= 0L || targetAmount <= 0L) {
            return 0
        }

        return BigDecimal.valueOf(amount)
            .multiply(ONE_HUNDRED)
            .divide(BigDecimal.valueOf(targetAmount), 0, RoundingMode.DOWN)
            .min(MAX_INT)
            .toInt()
    }

    companion object {
        private const val MAX_ACTIVE_FUNDING_COUNT = 5
        private val ONE_HUNDRED = BigDecimal.valueOf(100L)
        private val MAX_INT = BigDecimal.valueOf(Int.MAX_VALUE.toLong())
        private val HOME_CANDIDATE_STATUSES = setOf(
            FundingStatus.FUNDING,
            FundingStatus.CLOSED,
            FundingStatus.GIFT_PREPARING,
            FundingStatus.WAITING_REVEAL,
        )
        private val BIRTHDAY_CLOSED_CANDIDATE_STATUSES = setOf(
            FundingStatus.FUNDING,
            FundingStatus.CLOSED,
        )
        private val SURPRISE_ACTIVE_STATUSES = HOME_CANDIDATE_STATUSES
    }
}
