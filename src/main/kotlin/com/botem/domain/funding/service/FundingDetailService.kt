package com.botem.domain.funding.service

import com.botem.domain.funding.dto.response.FundingDetailGiftVerificationResponse
import com.botem.domain.funding.dto.response.FundingDetailParticipantResponse
import com.botem.domain.funding.dto.response.FundingDetailProductResponse
import com.botem.domain.funding.dto.response.FundingDetailResponse
import com.botem.domain.funding.dto.response.FundingDetailThankCardResponse
import com.botem.domain.funding.dto.response.FundingDisplayStatus
import com.botem.domain.funding.entity.Contribution
import com.botem.domain.funding.entity.ContributionStatus
import com.botem.domain.funding.entity.Funding
import com.botem.domain.funding.entity.FundingStatus
import com.botem.domain.funding.entity.FundingType
import com.botem.domain.funding.entity.GiftVerificationStatus
import com.botem.domain.funding.exception.FundingErrorCode
import com.botem.domain.funding.repository.ContributionRepository
import com.botem.domain.funding.repository.FundingRepository
import com.botem.domain.funding.repository.GiftVerificationRepository
import com.botem.domain.funding.repository.SurpriseThankCardRepository
import com.botem.domain.user.repository.UserRepository
import com.botem.global.exception.BusinessException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

@Service
class FundingDetailService(
    private val fundingRepository: FundingRepository,
    private val contributionRepository: ContributionRepository,
    private val userRepository: UserRepository,
    private val giftVerificationRepository: GiftVerificationRepository,
    private val surpriseThankCardRepository: SurpriseThankCardRepository,
    private val clock: Clock,
) {

    @Transactional(readOnly = true)
    fun getDetail(viewerId: Long?, fundingId: Long): FundingDetailResponse {
        val now = LocalDateTime.now(clock)
        val funding = fundingRepository.findById(fundingId)
            .filter { it.status != FundingStatus.CANCELLED }
            .orElseThrow { BusinessException(FundingErrorCode.FUNDING_NOT_FOUND) }

        val contributions = contributionRepository.findAllByFundingIdOrderByCreatedAtAscIdAsc(fundingId)
            .filter { it.status != ContributionStatus.CANCELLED }
        val confirmedAmount = contributions.sumAmount(ContributionStatus.CONFIRMED)
        val pendingAmount = contributions.sumAmount(ContributionStatus.PENDING_CONFIRMATION)
        val participants = toParticipants(contributions)

        val displayStatus = displayStatus(funding, confirmedAmount, now)
        val beforeDeadline = funding.deadline.isAfter(now)
        val participationOpen = beforeDeadline && funding.status in PARTICIPATABLE_STATUSES

        return FundingDetailResponse(
            fundingId = funding.id,
            type = funding.type,
            title = funding.title,
            product = FundingDetailProductResponse.from(funding.product),
            productChanged = funding.productChangedAt != null,
            productChangedAt = funding.productChangedAt,
            story = funding.story,
            promise = funding.promise,
            deadline = funding.deadline,
            remainingDays = if (beforeDeadline) {
                ChronoUnit.DAYS.between(now.toLocalDate(), funding.deadline.toLocalDate())
            } else {
                null
            },
            confirmedPercentage = progressPercentage(confirmedAmount, funding.targetAmount),
            pendingPercentage = progressPercentage(pendingAmount, funding.targetAmount),
            participantCount = participants.size,
            participants = participants,
            displayStatus = displayStatus,
            canContributeAmount = participationOpen && funding.earlyClosedAt == null &&
                confirmedAmount < funding.targetAmount,
            canParticipate = participationOpen,
            ownerPreview = viewerId != null && viewerId == funding.ownerId,
            giftVerification = findCompletedGiftVerification(funding),
            thankCard = findThankCard(funding),
        )
    }

    // 인증을 마친 펀딩만 COMPLETED이므로 그 외 상태에서는 조회하지 않는다
    private fun findCompletedGiftVerification(funding: Funding): FundingDetailGiftVerificationResponse? {
        if (funding.status != FundingStatus.COMPLETED) {
            return null
        }

        return giftVerificationRepository.findByFundingId(funding.id)
            ?.takeIf { it.status == GiftVerificationStatus.COMPLETED }
            ?.let(FundingDetailGiftVerificationResponse::from)
    }

    private fun findThankCard(funding: Funding): FundingDetailThankCardResponse? {
        if (funding.type != FundingType.SURPRISE) {
            return null
        }

        return surpriseThankCardRepository.findByFundingId(funding.id)
            ?.let(FundingDetailThankCardResponse::from)
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
        when {
            funding.status == FundingStatus.COMPLETED -> FundingDisplayStatus.COMPLETED
            funding.earlyClosedAt != null && funding.status != FundingStatus.GIFT_PREPARING ->
                FundingDisplayStatus.CLOSED
            funding.status in PARTICIPATABLE_STATUSES -> when {
                !funding.deadline.isAfter(now) -> FundingDisplayStatus.CLOSED
                confirmedAmount >= funding.targetAmount -> FundingDisplayStatus.TARGET_REACHED
                else -> FundingDisplayStatus.IN_PROGRESS
            }
            else -> FundingDisplayStatus.GIFT_PREPARING
        }

    private fun List<Contribution>.sumAmount(status: ContributionStatus): Long =
        filter { it.status == status }.sumOf(Contribution::amount)

    companion object {
        private val PARTICIPATABLE_STATUSES = setOf(FundingStatus.FUNDING, FundingStatus.CLOSED)
    }
}
