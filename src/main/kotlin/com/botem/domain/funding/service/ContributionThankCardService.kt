package com.botem.domain.funding.service

import com.botem.domain.funding.dto.request.SendContributionThankCardRequest
import com.botem.domain.funding.dto.response.ContributionThankCardDetailResponse
import com.botem.domain.funding.dto.response.ContributionThankCardResponse
import com.botem.domain.funding.dto.response.RollingPaperMessageType
import com.botem.domain.funding.dto.response.SurpriseThankCardFriendResponse
import com.botem.domain.funding.dto.response.SurpriseThankCardFriendsResponse
import com.botem.domain.funding.entity.Contribution
import com.botem.domain.funding.entity.ContributionStatus
import com.botem.domain.funding.entity.ContributionThankCard
import com.botem.domain.funding.entity.Funding
import com.botem.domain.funding.entity.FundingStatus
import com.botem.domain.funding.entity.FundingType
import com.botem.domain.funding.entity.RollingPaperMessage
import com.botem.domain.funding.exception.FundingErrorCode
import com.botem.domain.funding.repository.ContributionRepository
import com.botem.domain.funding.repository.ContributionThankCardRepository
import com.botem.domain.funding.repository.FundingRepository
import com.botem.domain.funding.repository.RollingPaperMessageRepository
import com.botem.domain.user.exception.UserErrorCode
import com.botem.domain.user.repository.UserRepository
import com.botem.global.exception.BusinessException
import com.botem.global.exception.CommonErrorCode
import com.botem.global.property.ContributionThankCardProperties
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId

@Service
class ContributionThankCardService(
    private val fundingRepository: FundingRepository,
    private val contributionRepository: ContributionRepository,
    private val contributionThankCardRepository: ContributionThankCardRepository,
    private val rollingPaperMessageRepository: RollingPaperMessageRepository,
    private val userRepository: UserRepository,
    private val contributionThankCardProperties: ContributionThankCardProperties,
    private val clock: Clock,
) {

    @Transactional
    fun send(
        userId: Long,
        fundingId: Long,
        contributionId: Long,
        request: SendContributionThankCardRequest,
    ): ContributionThankCardResponse {
        val funding = fundingRepository.findByIdForUpdate(fundingId)
            ?.takeIf { it.status != FundingStatus.CANCELLED }
            ?: throw BusinessException(FundingErrorCode.FUNDING_NOT_FOUND)
        validateSender(funding, userId)

        val contribution = contributionRepository.findByIdAndFundingIdForUpdate(contributionId, fundingId)
            ?: throw BusinessException(FundingErrorCode.CONTRIBUTION_NOT_FOUND)
        if (contribution.status != ContributionStatus.CONFIRMED) {
            throw BusinessException(FundingErrorCode.CONTRIBUTION_THANK_CARD_CONFIRMED_ONLY)
        }

        contributionThankCardRepository.findByContributionId(contributionId)?.let {
            return toSendResponse(it)
        }

        val message = normalizeMessage(request.message)
        val thankCard = contributionThankCardRepository.save(
            ContributionThankCard(
                contributionId = contributionId,
                senderUserId = userId,
                message = message,
                sentAt = LocalDateTime.now(clock),
                paperColor = request.paperColor,
            ),
        )

        return toSendResponse(thankCard)
    }

    private fun validateSender(funding: Funding, userId: Long) {
        when (funding.type) {
            FundingType.BIRTHDAY -> {
                if (funding.ownerId != userId) {
                    throw BusinessException(FundingErrorCode.FUNDING_OWNER_ONLY)
                }
            }

            FundingType.SURPRISE -> {
                funding.validateSurpriseRevealAccess(userId, LocalDateTime.now(clock))
                if (funding.surpriseOpenedAt == null) {
                    throw BusinessException(FundingErrorCode.SURPRISE_NOT_OPENED)
                }
            }
        }
    }

    @Transactional(readOnly = true)
    fun getForParticipant(userId: Long, contributionId: Long): ContributionThankCardDetailResponse {
        val contribution = contributionRepository.findByIdAndParticipantUserId(contributionId, userId)
            ?: throw BusinessException(FundingErrorCode.CONTRIBUTION_THANK_CARD_NOT_FOUND)
        val thankCard = contributionThankCardRepository.findByContributionId(contributionId)
            ?: throw BusinessException(FundingErrorCode.CONTRIBUTION_THANK_CARD_NOT_FOUND)

        return toDetailResponse(thankCard, contribution)
    }

    @Transactional(readOnly = true)
    fun getByShareKey(shareKey: String): ContributionThankCardDetailResponse {
        val thankCard = contributionThankCardRepository.findByShareKey(shareKey)
            ?: throw BusinessException(FundingErrorCode.CONTRIBUTION_THANK_CARD_NOT_FOUND)
        val contribution = contributionRepository.findById(thankCard.contributionId)
            .orElseThrow { BusinessException(FundingErrorCode.CONTRIBUTION_THANK_CARD_NOT_FOUND) }

        return toDetailResponse(thankCard, contribution)
    }

    @Transactional(readOnly = true)
    fun getSurpriseThankCardFriends(userId: Long, fundingId: Long): SurpriseThankCardFriendsResponse {
        val funding = fundingRepository.findById(fundingId)
            .orElse(null)
            ?.takeIf { it.status != FundingStatus.CANCELLED }
            ?: throw BusinessException(FundingErrorCode.FUNDING_NOT_FOUND)
        funding.validateSurpriseRevealAccess(userId, LocalDateTime.now(clock))
        if (funding.surpriseOpenedAt == null) {
            throw BusinessException(FundingErrorCode.SURPRISE_NOT_OPENED)
        }

        val contributions = contributionRepository.findAllByFundingIdOrderByCreatedAtAscIdAsc(fundingId)
            .filter { it.status != ContributionStatus.CANCELLED }
        if (contributions.isEmpty()) {
            return SurpriseThankCardFriendsResponse(fundingId, emptyList())
        }
        val contributionIds = contributions.map(Contribution::id)
        val messagesByContributionId = rollingPaperMessageRepository.findAllByContributionIdIn(contributionIds)
            .associateBy { it.contributionId }
        val thankCardContributionIds = contributionThankCardRepository.findAllByContributionIdIn(contributionIds)
            .mapTo(mutableSetOf()) { it.contributionId }

        return SurpriseThankCardFriendsResponse(
            fundingId = fundingId,
            friends = contributions.map { contribution ->
                SurpriseThankCardFriendResponse(
                    contributionId = contribution.id,
                    name = contribution.participantName,
                    organizer = contribution.participantUserId == funding.ownerId,
                    messageTypes = messageTypesOf(messagesByContributionId[contribution.id]),
                    contributedDate = contribution.createdAt
                        .atZone(ZoneId.systemDefault())
                        .withZoneSameInstant(KOREA_ZONE)
                        .toLocalDate(),
                    thankCardSent = contribution.id in thankCardContributionIds,
                )
            },
        )
    }

    private fun toDetailResponse(
        thankCard: ContributionThankCard,
        contribution: Contribution,
    ): ContributionThankCardDetailResponse {
        val funding = fundingRepository.findById(contribution.fundingId)
            .orElseThrow { BusinessException(FundingErrorCode.CONTRIBUTION_THANK_CARD_NOT_FOUND) }
        val sender = userRepository.findById(thankCard.senderUserId)
            .orElseThrow { BusinessException(UserErrorCode.USER_NOT_FOUND) }

        return ContributionThankCardDetailResponse.of(
            thankCard = thankCard,
            contribution = contribution,
            funding = funding,
            sender = sender,
            shareUrl = createShareUrl(thankCard.shareKey),
        )
    }

    private fun toSendResponse(thankCard: ContributionThankCard): ContributionThankCardResponse =
        ContributionThankCardResponse.from(thankCard, createShareUrl(thankCard.shareKey))

    private fun messageTypesOf(message: RollingPaperMessage?): List<RollingPaperMessageType> =
        buildList {
            if (!message?.content.isNullOrBlank()) add(RollingPaperMessageType.TEXT)
            if (message?.photoUrl != null) add(RollingPaperMessageType.PHOTO)
            if (message?.voiceUrl != null) add(RollingPaperMessageType.VOICE)
            if (message?.decorationCode != null) add(RollingPaperMessageType.STICKER)
        }

    private fun createShareUrl(shareKey: String): String =
        "${contributionThankCardProperties.baseUrl.trimEnd('/')}/$shareKey"

    private fun normalizeMessage(message: String): String {
        val normalized = message.trim { it.isWhitespace() || Character.isSpaceChar(it) }
        if (normalized.isEmpty() || !ContributionThankCard.hasValidMessageLength(normalized)) {
            throw BusinessException(
                CommonErrorCode.INVALID_INPUT_VALUE,
                "message: ${ContributionThankCard.MAX_MESSAGE_LENGTH}자 이내로 입력해 주세요",
            )
        }
        return normalized
    }

    companion object {
        private val KOREA_ZONE = ZoneId.of("Asia/Seoul")
    }
}
