package com.botem.domain.funding.repository

import com.botem.domain.funding.entity.GiftVerification
import com.botem.domain.funding.entity.GiftVerificationStatus
import com.botem.domain.funding.entity.ContributionStatus
import com.botem.domain.funding.repository.projection.PendingGiftVerificationTarget
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface GiftVerificationRepository : JpaRepository<GiftVerification, Long> {

    fun findByFundingId(fundingId: Long): GiftVerification?

    @Query(
        """
        SELECT COUNT(verification.id)
        FROM GiftVerification verification
        JOIN Funding funding ON funding.id = verification.fundingId
        WHERE verification.status = :status
          AND (
            (funding.type = com.botem.domain.funding.entity.FundingType.BIRTHDAY AND funding.ownerId = :userId)
            OR
            (funding.type = com.botem.domain.funding.entity.FundingType.SURPRISE AND funding.recipientId = :userId)
          )
        """,
    )
    fun countByRecipientAndStatus(
        @Param("userId") userId: Long,
        @Param("status") status: GiftVerificationStatus,
    ): Long

    @Query(
        """
        SELECT new com.botem.domain.funding.repository.projection.PendingGiftVerificationTarget(
            verification.id,
            funding.id,
            funding.product.name,
            funding.product.imageUrl,
            funding.deadline,
            COUNT(contribution.id),
            verification.replacementProductName
        )
        FROM GiftVerification verification
        JOIN Funding funding ON funding.id = verification.fundingId
        LEFT JOIN Contribution contribution
          ON contribution.fundingId = funding.id
          AND contribution.status NOT IN :excludedContributionStatuses
        WHERE verification.status = :status
          AND (
            (funding.type = com.botem.domain.funding.entity.FundingType.BIRTHDAY AND funding.ownerId = :userId)
            OR
            (funding.type = com.botem.domain.funding.entity.FundingType.SURPRISE AND funding.recipientId = :userId)
          )
        GROUP BY
            verification.id,
            funding.id,
            funding.product.name,
            funding.product.imageUrl,
            funding.deadline,
            verification.replacementProductName
        ORDER BY funding.deadline ASC, verification.id ASC
        """,
    )
    fun findTargetsByRecipientAndStatus(
        @Param("userId") userId: Long,
        @Param("status") status: GiftVerificationStatus,
        @Param("excludedContributionStatuses") excludedContributionStatuses: Collection<ContributionStatus>,
    ): List<PendingGiftVerificationTarget>
}
