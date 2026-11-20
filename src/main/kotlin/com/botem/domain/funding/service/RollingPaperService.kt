package com.botem.domain.funding.service

import com.botem.domain.funding.dto.response.RollingPaperMessageResponse
import com.botem.domain.funding.dto.response.RollingPaperResponse
import com.botem.domain.funding.entity.FundingStatus
import com.botem.domain.funding.entity.FundingType
import com.botem.domain.funding.exception.FundingErrorCode
import com.botem.domain.funding.repository.FundingRepository
import com.botem.domain.funding.repository.RollingPaperMessageRepository
import com.botem.domain.user.repository.UserRepository
import com.botem.global.exception.BusinessException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

// 생일 펀딩 롤링페이퍼는 개설자가 팡(결과)을 연 뒤에만 본다. 참여자 열람은 지난 펀딩 아카이브(#188)에서 넓힌다.
// 서프라이즈 받는 사람은 서프라이즈 공개 상세에서 같은 메시지 목록을 본다
@Service
class RollingPaperService(
    private val fundingRepository: FundingRepository,
    private val rollingPaperMessageRepository: RollingPaperMessageRepository,
    private val userRepository: UserRepository,
) {

    @Transactional(readOnly = true)
    fun getRollingPaper(userId: Long, fundingId: Long): RollingPaperResponse {
        val funding = fundingRepository.findById(fundingId).orElse(null)
            ?.takeIf { it.status != FundingStatus.CANCELLED }
            ?: throw BusinessException(FundingErrorCode.FUNDING_NOT_FOUND)
        if (funding.ownerId != userId) {
            throw BusinessException(FundingErrorCode.FUNDING_OWNER_ONLY)
        }
        if (funding.type != FundingType.BIRTHDAY) {
            throw BusinessException(FundingErrorCode.BIRTHDAY_FUNDING_REQUIRED)
        }
        if (funding.resultOpenedAt == null) {
            throw BusinessException(FundingErrorCode.ROLLING_PAPER_NOT_OPENED)
        }

        val owner = userRepository.findById(funding.ownerId).orElse(null)
        val messages = rollingPaperMessageRepository.findAllByFundingId(funding.id)
            .map(RollingPaperMessageResponse::from)

        return RollingPaperResponse(
            fundingId = funding.id,
            celebrantName = owner?.let { it.name ?: it.nickname }.orEmpty(),
            messageCount = messages.size,
            messages = messages,
        )
    }
}
