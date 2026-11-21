package com.botem.domain.funding.service

import com.botem.domain.funding.dto.response.RollingPaperMessageResponse
import com.botem.domain.funding.dto.response.RollingPaperResponse
import com.botem.domain.funding.entity.ContributionStatus
import com.botem.domain.funding.entity.Funding
import com.botem.domain.funding.entity.FundingStatus
import com.botem.domain.funding.entity.FundingType
import com.botem.domain.funding.exception.FundingErrorCode
import com.botem.domain.funding.repository.ContributionRepository
import com.botem.domain.funding.repository.FundingRepository
import com.botem.domain.funding.repository.RollingPaperMessageRepository
import com.botem.domain.user.repository.UserRepository
import com.botem.global.exception.BusinessException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

@Service
class RollingPaperService(
    private val fundingRepository: FundingRepository,
    private val contributionRepository: ContributionRepository,
    private val rollingPaperMessageRepository: RollingPaperMessageRepository,
    private val userRepository: UserRepository,
    private val clock: Clock,
) {

    @Transactional(readOnly = true)
    fun getRollingPaper(userId: Long, fundingId: Long): RollingPaperResponse {
        val funding = fundingRepository.findById(fundingId).orElse(null)
            ?.takeIf { it.status != FundingStatus.CANCELLED }
            ?: throw BusinessException(FundingErrorCode.FUNDING_NOT_FOUND)
        funding.validateRollingPaperOpened(LocalDateTime.now(clock))
        validateViewer(userId, funding)

        val celebrantId = if (funding.type == FundingType.BIRTHDAY) funding.ownerId else funding.recipientId
        val celebrant = celebrantId?.let(userRepository::findById)?.orElse(null)
        val messages = rollingPaperMessageRepository.findAllByFundingId(funding.id)
            .map(RollingPaperMessageResponse::from)

        return RollingPaperResponse(
            fundingId = funding.id,
            celebrantName = celebrant?.let { it.name ?: it.nickname }.orEmpty(),
            messageCount = messages.size,
            messages = messages,
        )
    }

    private fun validateViewer(userId: Long, funding: Funding) {
        if (funding.ownerId == userId || funding.recipientId == userId) {
            return
        }
        val isParticipant = contributionRepository.existsByFundingIdAndParticipantUserIdAndStatusNot(
            fundingId = funding.id,
            participantUserId = userId,
            status = ContributionStatus.CANCELLED,
        )
        if (!isParticipant) {
            throw BusinessException(FundingErrorCode.ROLLING_PAPER_VIEWER_ONLY)
        }
    }

    private fun Funding.validateRollingPaperOpened(now: LocalDateTime) {
        when (type) {
            FundingType.BIRTHDAY -> {
                if (resultOpenedAt == null) {
                    throw BusinessException(FundingErrorCode.ROLLING_PAPER_NOT_OPENED)
                }
            }

            FundingType.SURPRISE -> {
                if (revealAt?.isAfter(now) != false || !isSurpriseGiftPrepared()) {
                    throw BusinessException(FundingErrorCode.SURPRISE_NOT_REVEALED)
                }
            }
        }
    }
}
