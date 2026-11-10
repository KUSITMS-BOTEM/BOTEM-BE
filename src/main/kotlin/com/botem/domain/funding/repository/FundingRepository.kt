package com.botem.domain.funding.repository

import com.botem.domain.funding.entity.Funding
import com.botem.domain.funding.entity.FundingStatus
import com.botem.domain.funding.repository.projection.RevealedSurpriseCardProjection
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface FundingRepository : JpaRepository<Funding, Long> {

    fun findAllByOwnerIdAndStatusIn(
        ownerId: Long,
        statuses: Collection<FundingStatus>,
    ): List<Funding>

    fun findAllByOwnerIdAndProductSourceWishIdInAndStatusNotIn(
        ownerId: Long,
        sourceWishIds: Collection<Long>,
        statuses: Collection<FundingStatus>,
    ): List<Funding>

    fun existsByOwnerIdAndProductSourceWishIdAndStatusNotIn(
        ownerId: Long,
        sourceWishId: Long,
        statuses: Collection<FundingStatus>,
    ): Boolean

    @Query(
        """
        SELECT new com.botem.domain.funding.repository.projection.RevealedSurpriseCardProjection(
            funding.id,
            funding.product.imageUrl,
            funding.product.name,
            COALESCE(owner.name, owner.nickname),
            COUNT(contribution.id),
            funding.revealAt
        )
        FROM Funding funding
        JOIN User owner ON owner.id = funding.ownerId
        LEFT JOIN Contribution contribution
          ON contribution.fundingId = funding.id
          AND contribution.status <> com.botem.domain.funding.entity.ContributionStatus.CANCELLED
        WHERE funding.type = com.botem.domain.funding.entity.FundingType.SURPRISE
          AND funding.recipientId = :recipientId
          AND funding.revealAt <= :now
          AND funding.status <> com.botem.domain.funding.entity.FundingStatus.COMPLETED
          AND funding.status <> com.botem.domain.funding.entity.FundingStatus.CANCELLED
        GROUP BY
            funding.id,
            funding.product.imageUrl,
            funding.product.name,
            owner.name,
            owner.nickname,
            funding.revealAt
        ORDER BY funding.revealAt ASC, funding.id ASC
        """,
    )
    fun findRevealedSurpriseCards(
        @Param("recipientId") recipientId: Long,
        @Param("now") now: LocalDateTime,
    ): List<RevealedSurpriseCardProjection>
}
