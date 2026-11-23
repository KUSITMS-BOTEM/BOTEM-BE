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

interface FundingRepository : JpaRepository<Funding, Long>, FundingLifecycleQueryRepository {

    @Query("SELECT funding.id FROM Funding funding WHERE funding.shareKey = :shareKey")
    fun findIdByShareKey(@Param("shareKey") shareKey: String): Long?

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

    fun existsByRecipientIdAndTypeAndRevealAtAfterAndStatusNotIn(
        recipientId: Long,
        type: FundingType,
        now: LocalDateTime,
        statuses: Collection<FundingStatus>,
    ): Boolean

    // 친구당 진행 중 서프라이즈는 1개이므로 첫 건만 조회한다 (공개 시각 전 + 끝나지 않은 상태)
    fun findFirstByRecipientIdAndTypeAndRevealAtAfterAndStatusNotIn(
        recipientId: Long,
        type: FundingType,
        now: LocalDateTime,
        statuses: Collection<FundingStatus>,
    ): Funding?

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
          AND funding.status IN (
            com.botem.domain.funding.entity.FundingStatus.WAITING_REVEAL,
            com.botem.domain.funding.entity.FundingStatus.REVEALED,
            com.botem.domain.funding.entity.FundingStatus.WAITING_GIFT_VERIFICATION
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
