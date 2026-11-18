package com.botem.domain.funding.repository

import com.botem.domain.funding.entity.Funding
import com.botem.domain.funding.entity.FundingStatus
import com.botem.domain.funding.entity.FundingType
import com.botem.domain.funding.repository.projection.RevealedSurpriseCardProjection
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface FundingRepository : JpaRepository<Funding, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT funding FROM Funding funding WHERE funding.id = :fundingId")
    fun findByIdForUpdate(@Param("fundingId") fundingId: Long): Funding?

    fun existsByOwnerIdAndStatusNotIn(
        ownerId: Long,
        statuses: Collection<FundingStatus>,
    ): Boolean

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

    fun existsByRecipientIdAndTypeAndStatusNotIn(
        recipientId: Long,
        type: FundingType,
        statuses: Collection<FundingStatus>,
    ): Boolean

    @Query(
        """
        SELECT COUNT(funding)
        FROM Funding funding
        WHERE funding.ownerId = :ownerId
          AND (
            (
              funding.type = com.botem.domain.funding.entity.FundingType.BIRTHDAY
              AND funding.status IN (
                com.botem.domain.funding.entity.FundingStatus.FUNDING,
                com.botem.domain.funding.entity.FundingStatus.CLOSED
              )
              AND funding.deadline > :now
            )
            OR
            (
              funding.type = com.botem.domain.funding.entity.FundingType.SURPRISE
              AND funding.status IN (
                com.botem.domain.funding.entity.FundingStatus.FUNDING,
                com.botem.domain.funding.entity.FundingStatus.CLOSED,
                com.botem.domain.funding.entity.FundingStatus.GIFT_PREPARING,
                com.botem.domain.funding.entity.FundingStatus.WAITING_REVEAL
              )
              AND funding.revealAt > :now
            )
          )
        """,
    )
    fun countActiveFundings(
        @Param("ownerId") ownerId: Long,
        @Param("now") now: LocalDateTime,
    ): Long

    @Query(
        """
        SELECT new com.botem.domain.funding.repository.projection.RevealedSurpriseCardProjection(
            funding.id,
            funding.product.imageUrl,
            funding.product.name,
            COALESCE(owner.name, owner.nickname),
            COUNT(DISTINCT contribution.participantUserId)
                + COALESCE(SUM(CASE
                    WHEN contribution.id IS NOT NULL AND contribution.participantUserId IS NULL
                    THEN 1 ELSE 0 END), 0),
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
          AND NOT EXISTS (
              SELECT thankCard.id
              FROM SurpriseThankCard thankCard
              WHERE thankCard.fundingId = funding.id
          )
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
