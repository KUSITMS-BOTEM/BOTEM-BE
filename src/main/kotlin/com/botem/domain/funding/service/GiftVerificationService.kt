package com.botem.domain.funding.service

import com.botem.domain.funding.dto.request.CompleteGiftVerificationRequest
import com.botem.domain.funding.dto.response.GiftVerificationResponse
import com.botem.domain.funding.entity.GiftVerificationStatus
import com.botem.domain.funding.exception.FundingErrorCode
import com.botem.domain.funding.repository.FundingRepository
import com.botem.domain.funding.repository.GiftVerificationRepository
import com.botem.global.exception.BusinessException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

@Service
class GiftVerificationService(
    private val giftVerificationRepository: GiftVerificationRepository,
    private val fundingRepository: FundingRepository,
    private val giftVerificationImageService: GiftVerificationImageService,
    private val clock: Clock,
) {

    @Transactional
    fun complete(
        userId: Long,
        giftVerificationId: Long,
        request: CompleteGiftVerificationRequest,
    ): GiftVerificationResponse {
        val verification = giftVerificationRepository.findByIdForUpdate(giftVerificationId)
            ?: throw BusinessException(FundingErrorCode.GIFT_VERIFICATION_NOT_FOUND)
        val funding = fundingRepository.findByIdForUpdate(verification.fundingId)
            ?: throw BusinessException(FundingErrorCode.FUNDING_NOT_FOUND)
        if (!funding.isGiftRecipient(userId)) {
            throw BusinessException(FundingErrorCode.GIFT_VERIFICATION_RECIPIENT_ONLY)
        }
        funding.validateGiftVerificationCompletable()

        val completedAt = LocalDateTime.now(clock)
        if (verification.status == GiftVerificationStatus.PENDING) {
            giftVerificationImageService.validateUploadedImage(userId, giftVerificationId, request.imageUrl)
            verification.complete(
                imageUrl = request.imageUrl,
                thankYouMessage = request.thankYouMessage?.trim()?.takeIf(String::isNotEmpty),
                purchasedThroughBotemLink = request.purchasedThroughBotemLink,
                completedAt = completedAt,
                replacementProductName = request.replacementProductName?.trim()?.takeIf(String::isNotEmpty),
            )
        }
        funding.completeGiftVerification(completedAt)

        return GiftVerificationResponse.from(verification)
    }
}
