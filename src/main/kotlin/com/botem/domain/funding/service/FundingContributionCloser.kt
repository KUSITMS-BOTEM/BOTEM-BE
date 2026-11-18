package com.botem.domain.funding.service

import com.botem.domain.funding.entity.Funding
import com.botem.domain.funding.repository.ContributionRepository
import org.springframework.stereotype.Component
import java.time.LocalDateTime

// 보태기가 마감되는 순간 송금 전 기록을 메시지 전용으로 바꿔, 기록이 확정·확인 대기·메시지 전용 중 하나로만 남게 한다.
// 펀딩 행을 잠근 트랜잭션 안에서 호출해야 한다
@Component
class FundingContributionCloser(
    private val contributionRepository: ContributionRepository,
) {

    fun close(funding: Funding, now: LocalDateTime) {
        funding.closeContributions()
        contributionRepository.convertAwaitingTransfersToMessageOnly(funding.id, now)
    }
}
