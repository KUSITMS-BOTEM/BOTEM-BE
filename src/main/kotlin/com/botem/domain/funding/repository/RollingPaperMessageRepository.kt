package com.botem.domain.funding.repository

import com.botem.domain.funding.entity.RollingPaperMessage
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface RollingPaperMessageRepository : JpaRepository<RollingPaperMessage, Long> {

    @Query(
        """
        SELECT message
        FROM RollingPaperMessage message
        JOIN Contribution contribution ON contribution.id = message.contributionId
        WHERE contribution.fundingId = :fundingId
          AND contribution.status <> com.botem.domain.funding.entity.ContributionStatus.CANCELLED
        ORDER BY contribution.createdAt ASC, contribution.id ASC
        """,
    )
    fun findAllByFundingId(@Param("fundingId") fundingId: Long): List<RollingPaperMessage>
}
