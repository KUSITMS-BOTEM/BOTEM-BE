package com.botem.domain.funding.service

import com.botem.domain.funding.dto.response.FundingDetailParticipantResponse
import com.botem.domain.funding.dto.response.FundingDetailProductResponse
import com.botem.domain.funding.dto.response.FundingDetailResponse
import com.botem.domain.funding.dto.response.FundingDisplayStatus
import com.botem.domain.funding.entity.Contribution
import com.botem.domain.funding.entity.ContributionStatus
import com.botem.domain.funding.entity.Funding
import com.botem.domain.funding.entity.FundingStatus
import com.botem.domain.funding.exception.FundingErrorCode
import com.botem.domain.funding.repository.ContributionRepository
import com.botem.domain.funding.repository.FundingRepository
import com.botem.domain.user.repository.UserRepository
import com.botem.global.exception.BusinessException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

@Service
class FundingDetailService(
    private val fundingRepository: FundingRepository,
    private val contributionRepository: ContributionRepository,
    private val userRepository: UserRepository,
    private val clock: Clock,
) {

    @Transactional(readOnly = true)
    fun getDetail(viewerId: Long?, fundingId: Long): FundingDetailResponse {
        val now = LocalDateTime.now(clock)
        val funding = fundingRepository.findById(fundingId)
            .filter { it.status != FundingStatus.CANCELLED }
            .orElseThrow { BusinessException(FundingErrorCode.FUNDING_NOT_FOUND) }

        val contributions = contributionRepository.findAllByFundingIdOrderByCreatedAtAsc(fundingId)
            .filter { it.status != ContributionStatus.CANCELLED }
        val confirmedAmount = contributions.sumAmount(ContributionStatus.CONFIRMED)
        val pendingAmount = contributions.sumAmount(ContributionStatus.PENDING_CONFIRMATION)
        val participants = toParticipants(contributions)

        val displayStatus = displayStatus(funding, confirmedAmount, now)
        val beforeDeadline = funding.deadline.isAfter(now)

        return FundingDetailResponse(
            fundingId = funding.id,
            type = funding.type,
            title = funding.title,
            product = FundingDetailProductResponse.from(funding.product),
            story = funding.story,
            promise = funding.promise,
            deadline = funding.deadline,
            remainingDays = if (beforeDeadline) {
                ChronoUnit.DAYS.between(now.toLocalDate(), funding.deadline.toLocalDate())
            } else {
                null
            },
            confirmedPercentage = percentage(confirmedAmount, funding.targetAmount),
            pendingPercentage = percentage(pendingAmount, funding.targetAmount),
            participantCount = participants.size,
            participants = participants,
            displayStatus = displayStatus,
            canContributeAmount = displayStatus == FundingDisplayStatus.IN_PROGRESS,
            canParticipate = displayStatus == FundingDisplayStatus.IN_PROGRESS ||
                displayStatus == FundingDisplayStatus.TARGET_REACHED,
            ownerPreview = viewerId != null && viewerId == funding.ownerId,
        )
    }

    // 로그인 참여자는 사용자 기준으로 첫 참여만 남기고, 비로그인 참여는 각각 한 명으로 센다
    private fun toParticipants(contributions: List<Contribution>): List<FundingDetailParticipantResponse> {
        val seenUserIds = mutableSetOf<Long>()
        val firstContributions = contributions.filter { contribution ->
            contribution.participantUserId?.let(seenUserIds::add) ?: true
        }
        val profileImageUrls = userRepository.findAllById(seenUserIds)
            .associate { it.id to it.profileImageUrl }

        return firstContributions.mapIndexed { index, contribution ->
            FundingDetailParticipantResponse(
                order = index + 1,
                name = contribution.participantName,
                profileImageUrl = contribution.participantUserId?.let(profileImageUrls::get),
            )
        }
    }

    private fun displayStatus(
        funding: Funding,
        confirmedAmount: Long,
        now: LocalDateTime,
    ): FundingDisplayStatus =
        when (funding.status) {
            FundingStatus.COMPLETED -> FundingDisplayStatus.COMPLETED
            FundingStatus.FUNDING, FundingStatus.CLOSED -> when {
                !funding.deadline.isAfter(now) -> FundingDisplayStatus.CLOSED
                confirmedAmount >= funding.targetAmount -> FundingDisplayStatus.TARGET_REACHED
                else -> FundingDisplayStatus.IN_PROGRESS
            }
            else -> FundingDisplayStatus.GIFT_PREPARING
        }

    private fun List<Contribution>.sumAmount(status: ContributionStatus): Long =
        filter { it.status == status }.sumOf(Contribution::amount)

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
        private val ONE_HUNDRED = BigDecimal.valueOf(100L)
        private val MAX_INT = BigDecimal.valueOf(Int.MAX_VALUE.toLong())
    }
}
