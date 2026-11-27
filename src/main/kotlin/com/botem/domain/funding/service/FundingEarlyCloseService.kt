package com.botem.domain.funding.service

import com.botem.domain.funding.dto.response.FundingEarlyCloseResponse
import com.botem.domain.funding.entity.ContributionStatus
import com.botem.domain.funding.entity.FundingStatus
import com.botem.domain.funding.exception.FundingErrorCode
import com.botem.domain.funding.repository.ContributionRepository
import com.botem.domain.funding.repository.FundingRepository
import com.botem.global.exception.BusinessException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

@Service
class FundingEarlyCloseService(
    private val fundingRepository: FundingRepository,
    private val contributionRepository: ContributionRepository,
    private val fundingContributionCloser: FundingContributionCloser,
    private val clock: Clock,
) {

    // 마감일 전에는 조기 마감, 마감일 경과 또는 조기 마감 뒤 참여자 0명 펀딩은 종료로 처리한다 ('펀딩 마감하기' 버튼 하나로 두 경우를 받는다)
    @Transactional
    fun close(
        userId: Long,
        fundingId: Long,
    ): FundingEarlyCloseResponse {
        val lockedFunding = fundingRepository.findByIdForUpdate(fundingId)
        // 잠금 대기 중에 마감일이 지날 수 있으므로 마감 판정 시각은 잠금을 얻은 뒤에 읽는다
        val now = LocalDateTime.now(clock)
        // 참여자 0명으로 종료한 펀딩만 다시 요청해도 같은 결과를 돌려주고, 그 밖의 취소된 펀딩은 없는 펀딩으로 본다
        val funding = lockedFunding
            ?.takeIf { it.status != FundingStatus.CANCELLED || it.isEndedWithoutParticipants(now) }
            ?: throw BusinessException(FundingErrorCode.FUNDING_NOT_FOUND)
        if (funding.ownerId != userId) {
            throw BusinessException(FundingErrorCode.FUNDING_OWNER_ONLY)
        }

        if (funding.canEndWithoutParticipants(now)) {
            if (funding.status != FundingStatus.CANCELLED &&
                contributionRepository.existsByFundingIdAndStatusNot(funding.id, ContributionStatus.CANCELLED)
            ) {
                throw BusinessException(FundingErrorCode.FUNDING_END_NOT_AVAILABLE)
            }
            funding.endWithoutParticipants(now)
            return FundingEarlyCloseResponse.from(funding)
        }

        val alreadyClosedEarly = funding.earlyClosedAt != null
        funding.closeEarly(now)
        if (!alreadyClosedEarly) {
            fundingContributionCloser.close(funding, now)
        }

        return FundingEarlyCloseResponse.from(funding)
    }
}
