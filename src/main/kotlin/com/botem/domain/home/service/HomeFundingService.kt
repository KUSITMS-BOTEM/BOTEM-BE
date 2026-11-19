package com.botem.domain.home.service

import com.botem.domain.funding.entity.Funding
import com.botem.domain.funding.entity.FundingStatus
import com.botem.domain.funding.entity.FundingType
import com.botem.domain.funding.repository.ContributionRepository
import com.botem.domain.funding.repository.FundingRepository
import com.botem.domain.funding.repository.projection.FundingContributionSummary
import com.botem.domain.funding.service.progressPercentage
import com.botem.domain.home.dto.response.HomeFundingCardResponse
import com.botem.domain.home.dto.response.HomeFundingCardType
import com.botem.domain.home.dto.response.HomeFundingListResponse
import com.botem.domain.home.dto.response.HomeFundingParticipantPreviewResponse
import com.botem.domain.user.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

@Service
class HomeFundingService(
    private val fundingRepository: FundingRepository,
    private val contributionRepository: ContributionRepository,
    private val userRepository: UserRepository,
    private val clock: Clock,
) {

    @Transactional(readOnly = true)
    fun getHomeFundings(userId: Long): HomeFundingListResponse {
        val now = LocalDateTime.now(clock)
        val fundings = fundingRepository.findAllByOwnerIdAndStatusIn(userId, HOME_CANDIDATE_STATUSES)
        val summaries = getContributionSummaries(fundings)
        val participantPreviews = getParticipantPreviews(fundings)
        val recipientNames = getRecipientNames(fundings)
        val cards = fundings.mapNotNull { funding ->
            toCard(
                funding = funding,
                summary = summaries[funding.id] ?: FundingContributionSummary.empty(funding.id),
                participantPreviews = participantPreviews[funding.id].orEmpty(),
                recipientName = funding.recipientId?.let(recipientNames::get),
                now = now,
            )
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

    private fun getParticipantPreviews(
        fundings: List<Funding>,
    ): Map<Long, List<HomeFundingParticipantPreviewResponse>> {
        if (fundings.isEmpty()) {
            return emptyMap()
        }

        return contributionRepository.findParticipantPreviewsByFundingIds(fundings.map(Funding::id))
            .groupBy(
                keySelector = { it.fundingId },
                valueTransform = HomeFundingParticipantPreviewResponse::from,
            )
    }

    // 홈 펀딩 탭은 내가 연 펀딩만 보여주므로 서프라이즈 주최자에게 받는 사람 이름을 알려도 비밀이 새지 않는다
    private fun getRecipientNames(fundings: List<Funding>): Map<Long, String> {
        val recipientIds = fundings.mapNotNull(Funding::recipientId).toSet()
        if (recipientIds.isEmpty()) {
            return emptyMap()
        }

        return userRepository.findAllById(recipientIds).associate { it.id to (it.name ?: it.nickname) }
    }

    private fun toCard(
        funding: Funding,
        summary: FundingContributionSummary,
        participantPreviews: List<HomeFundingParticipantPreviewResponse>,
        recipientName: String?,
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
            participantPreviews = participantPreviews,
            remainingDays = remainingDays,
            confirmedPercentage = progressPercentage(summary.confirmedAmount, funding.targetAmount),
            pendingPercentage = progressPercentage(summary.pendingAmount, funding.targetAmount),
            pendingContributionCount = summary.pendingCount,
            recipientName = recipientName,
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

    companion object {
        private const val MAX_ACTIVE_FUNDING_COUNT = 5
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
