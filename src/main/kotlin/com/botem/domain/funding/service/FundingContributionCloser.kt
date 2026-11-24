package com.botem.domain.funding.service

import com.botem.domain.funding.entity.Funding
import com.botem.domain.funding.repository.ContributionRepository
import com.botem.global.entity.koreaTimeToAudit
import java.time.LocalDateTime
import org.springframework.stereotype.Component

// 보태기가 마감되는 순간 송금 전 기록을 메시지 전용으로 바꿔, 기록이 확정·확인 대기·메시지 전용 중 하나로만 남게 한다.
// 펀딩 행을 잠근 트랜잭션 안에서 호출해야 한다
@Component
class FundingContributionCloser(
    private val contributionRepository: ContributionRepository,
) {

    fun close(funding: Funding, now: LocalDateTime) {
        funding.closeContributions()
        // 일괄 갱신은 감사 기능을 거치지 않으므로 updatedAt을 직접 UTC로 맞춘다
        contributionRepository.convertAwaitingTransfersToMessageOnly(funding.id, now.koreaTimeToAudit())
    }
}
