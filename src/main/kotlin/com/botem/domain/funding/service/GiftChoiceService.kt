package com.botem.domain.funding.service

import com.botem.domain.funding.dto.response.GiftChoiceResponse
import com.botem.domain.funding.entity.FundingStatus
import com.botem.domain.funding.entity.FundingType
import com.botem.domain.funding.entity.GiftVerification
import com.botem.domain.funding.exception.FundingErrorCode
import com.botem.domain.funding.repository.FundingRepository
import com.botem.domain.funding.repository.GiftVerificationRepository
import com.botem.global.exception.BusinessException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

// 생일 펀딩은 구매(쿠팡 이동) 시 선물 인증 대기를 만들고, 서프라이즈는 수령자의 선물 선택으로 완료한다.
@Service
class GiftChoiceService(
    private val fundingRepository: FundingRepository,
    private val giftVerificationRepository: GiftVerificationRepository,
    private val clock: Clock,
) {

    @Transactional
    fun choose(userId: Long, fundingId: Long): GiftChoiceResponse {
        val funding = fundingRepository.findByIdForUpdate(fundingId)
            ?.takeIf { it.status != FundingStatus.CANCELLED }
            ?: throw BusinessException(FundingErrorCode.FUNDING_NOT_FOUND)
        val verification = when (funding.type) {
            FundingType.BIRTHDAY -> {
                if (funding.ownerId != userId) {
                    throw BusinessException(FundingErrorCode.FUNDING_OWNER_ONLY)
                }
                funding.chooseGift()
                giftVerificationRepository.findByFundingId(fundingId)
                    ?: giftVerificationRepository.save(GiftVerification(fundingId))
            }

            FundingType.SURPRISE -> {
                if (funding.recipientId != userId) {
                    throw BusinessException(FundingErrorCode.SURPRISE_RECIPIENT_ONLY)
                }
                if (funding.status != FundingStatus.REVEALED && funding.status != FundingStatus.COMPLETED) {
                    throw BusinessException(FundingErrorCode.GIFT_CHOICE_NOT_AVAILABLE)
                }
                funding.completeSurpriseGiftChoice(LocalDateTime.now(clock))
                null
            }
        }

        return GiftChoiceResponse.of(funding, verification)
    }
}
