package com.botem.domain.funding.repository

import com.botem.domain.funding.entity.Funding
import com.botem.domain.funding.entity.FundingStatus
import com.botem.domain.funding.entity.FundingType
import com.botem.domain.funding.repository.projection.ProfileFundingTypeProjection
import com.botem.domain.funding.repository.projection.RevealedSurpriseCardProjection
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface FundingRepository : JpaRepository<Funding, Long> {

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

    // 위시 프로필의 진행 중 펀딩: 본인 생일 펀딩과, 받는 사람 프로필 노출에 동의한 서프라이즈.
    // 서프라이즈는 받는 사람 본인이나 비로그인 방문자에게 드러나지 않도록 호출하는 쪽에서 includeSurprise로 제외한다
    @Query(
        """
        SELECT funding
        FROM Funding funding
        WHERE (
            funding.type = com.botem.domain.funding.entity.FundingType.BIRTHDAY
            AND funding.ownerId = :userId
            AND funding.status IN (
              com.botem.domain.funding.entity.FundingStatus.FUNDING,
              com.botem.domain.funding.entity.FundingStatus.CLOSED
            )
            AND funding.deadline > :now
          )
          OR (
            :includeSurprise = TRUE
            AND funding.type = com.botem.domain.funding.entity.FundingType.SURPRISE
            AND funding.recipientId = :userId
            AND funding.showOnRecipientProfile = TRUE
            AND funding.status IN (
              com.botem.domain.funding.entity.FundingStatus.FUNDING,
              com.botem.domain.funding.entity.FundingStatus.CLOSED,
              com.botem.domain.funding.entity.FundingStatus.GIFT_PREPARING,
              com.botem.domain.funding.entity.FundingStatus.WAITING_REVEAL
            )
            AND funding.revealAt > :now
          )
        ORDER BY funding.deadline ASC, funding.id ASC
        """,
    )
    fun findProfileFundings(
        @Param("userId") userId: Long,
        @Param("now") now: LocalDateTime,
        @Param("includeSurprise") includeSurprise: Boolean,
    ): List<Funding>

    // 친구 목록 배지용. 조건은 findProfileFundings(서프라이즈 포함)와 같아야 친구 프로필과 배지가 어긋나지 않는다
    @Query(
        """
        SELECT DISTINCT new com.botem.domain.funding.repository.projection.ProfileFundingTypeProjection(
            CASE
                WHEN funding.type = com.botem.domain.funding.entity.FundingType.BIRTHDAY THEN funding.ownerId
                ELSE funding.recipientId
            END,
            funding.type
        )
        FROM Funding funding
        WHERE (
            funding.type = com.botem.domain.funding.entity.FundingType.BIRTHDAY
            AND funding.ownerId IN :userIds
            AND funding.status IN (
              com.botem.domain.funding.entity.FundingStatus.FUNDING,
              com.botem.domain.funding.entity.FundingStatus.CLOSED
            )
            AND funding.deadline > :now
          )
          OR (
            funding.type = com.botem.domain.funding.entity.FundingType.SURPRISE
            AND funding.recipientId IN :userIds
            AND funding.showOnRecipientProfile = TRUE
            AND funding.status IN (
              com.botem.domain.funding.entity.FundingStatus.FUNDING,
              com.botem.domain.funding.entity.FundingStatus.CLOSED,
              com.botem.domain.funding.entity.FundingStatus.GIFT_PREPARING,
              com.botem.domain.funding.entity.FundingStatus.WAITING_REVEAL
            )
            AND funding.revealAt > :now
          )
        """,
    )
    fun findProfileFundingTypes(
        @Param("userIds") userIds: Collection<Long>,
        @Param("now") now: LocalDateTime,
    ): List<ProfileFundingTypeProjection>

    // 친구당 진행 중 서프라이즈는 1개이므로 첫 건만 조회한다 (공개 시각 전 + 끝나지 않은 상태)
    fun findFirstByRecipientIdAndTypeAndRevealAtAfterAndStatusNotIn(
        recipientId: Long,
        type: FundingType,
        now: LocalDateTime,
        statuses: Collection<FundingStatus>,
    ): Funding?

    // 홈의 진행 중 기준과 맞춰, 생일은 마감일 경과·조기 마감·마감 전 결과 열기, 서프라이즈는 공개 시각 경과를 마감으로 본다
    @Query(
        """
        SELECT funding
        FROM Funding funding
        WHERE funding.ownerId = :ownerId
          AND funding.status <> com.botem.domain.funding.entity.FundingStatus.CANCELLED
          AND (
            (
              funding.type = com.botem.domain.funding.entity.FundingType.BIRTHDAY
              AND (funding.deadline <= :now OR funding.earlyClosedAt IS NOT NULL OR funding.resultOpenedAt IS NOT NULL)
            )
            OR
            (
              funding.type = com.botem.domain.funding.entity.FundingType.SURPRISE
              AND funding.revealAt <= :now
            )
          )
        ORDER BY funding.deadline DESC, funding.id DESC
        """,
    )
    fun findClosedFundingsByOwnerId(
        @Param("ownerId") ownerId: Long,
        @Param("now") now: LocalDateTime,
    ): List<Funding>

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
