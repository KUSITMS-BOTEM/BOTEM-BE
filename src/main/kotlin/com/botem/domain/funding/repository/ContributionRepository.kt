package com.botem.domain.funding.repository

import com.botem.domain.funding.entity.Contribution
import com.botem.domain.funding.repository.projection.FundingContributionSummary
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ContributionRepository : JpaRepository<Contribution, Long> {

    fun findAllByFundingIdOrderByCreatedAtAsc(fundingId: Long): List<Contribution>

    @Query(
        """
        SELECT new com.botem.domain.funding.repository.projection.FundingContributionSummary(
            contribution.fundingId,
            COALESCE(SUM(CASE
                WHEN contribution.status = com.botem.domain.funding.entity.ContributionStatus.CONFIRMED
                THEN contribution.amount ELSE 0 END), 0),
            COALESCE(SUM(CASE
                WHEN contribution.status = com.botem.domain.funding.entity.ContributionStatus.PENDING_CONFIRMATION
                THEN contribution.amount ELSE 0 END), 0),
            COALESCE(SUM(CASE
                WHEN contribution.status = com.botem.domain.funding.entity.ContributionStatus.PENDING_CONFIRMATION
                THEN 1 ELSE 0 END), 0)
        )
        FROM Contribution contribution
        WHERE contribution.fundingId IN :fundingIds
        GROUP BY contribution.fundingId
        """,
    )
    fun summarizeByFundingIds(
        @Param("fundingIds") fundingIds: Collection<Long>,
    ): List<FundingContributionSummary>
}
