package com.botem.domain.funding.service

import com.botem.domain.funding.dto.response.GiftChoiceResponse
import com.botem.domain.funding.entity.FundingStatus
import com.botem.domain.funding.entity.GiftVerification
import com.botem.domain.funding.exception.FundingErrorCode
import com.botem.domain.funding.repository.FundingRepository
import com.botem.domain.funding.repository.GiftVerificationRepository
import com.botem.global.exception.BusinessException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

// 생일 펀딩 결과 화면에서 구매(쿠팡 이동)를 누르는 순간 지금 상품을 선물로 정하고 인증 대기를 만든다.
// 저장한 선택은 홈 '인증할 선물' 배너에서 구매 링크를 다시 보여주는 데 쓴다
@Service
class GiftChoiceService(
    private val fundingRepository: FundingRepository,
    private val giftVerificationRepository: GiftVerificationRepository,
) {

    @Transactional
    fun choose(userId: Long, fundingId: Long): GiftChoiceResponse {
        val funding = fundingRepository.findByIdForUpdate(fundingId)
            ?.takeIf { it.status != FundingStatus.CANCELLED }
            ?: throw BusinessException(FundingErrorCode.FUNDING_NOT_FOUND)
        if (funding.ownerId != userId) {
            throw BusinessException(FundingErrorCode.FUNDING_OWNER_ONLY)
        }

        funding.chooseGift()
        val verification = giftVerificationRepository.findByFundingId(fundingId)
            ?: giftVerificationRepository.save(GiftVerification(fundingId))

        return GiftChoiceResponse.of(funding, verification)
    }
}
