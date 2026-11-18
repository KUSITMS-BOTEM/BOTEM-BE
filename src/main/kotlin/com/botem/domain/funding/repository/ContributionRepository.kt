package com.botem.domain.funding.repository

import com.botem.domain.funding.entity.Contribution
import com.botem.domain.funding.entity.ContributionStatus
import com.botem.domain.funding.repository.projection.FundingContributionSummary
import com.botem.domain.funding.repository.projection.FundingParticipantPreviewProjection
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface ContributionRepository : JpaRepository<Contribution, Long> {

    fun findAllByFundingIdOrderByCreatedAtAscIdAsc(fundingId: Long): List<Contribution>

    fun findAllByFundingIdAndStatusOrderByCreatedAtAscIdAsc(
        fundingId: Long,
        status: ContributionStatus,
    ): List<Contribution>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        """
        SELECT contribution
        FROM Contribution contribution
        WHERE contribution.fundingId = :fundingId
          AND contribution.status = :status
        ORDER BY contribution.createdAt ASC, contribution.id ASC
        """,
    )
    fun findAllByFundingIdAndStatusForUpdate(
        @Param("fundingId") fundingId: Long,
        @Param("status") status: ContributionStatus,
    ): List<Contribution>

    fun findByFundingIdAndRequestKey(fundingId: Long, requestKey: String): Contribution?

    fun findAllByFundingIdAndParticipantUserIdIsNullAndParticipantNameAndStatusNot(
        fundingId: Long,
        participantName: String,
        status: ContributionStatus,
    ): List<Contribution>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        """
        SELECT contribution
        FROM Contribution contribution
        WHERE contribution.id = :contributionId
          AND contribution.fundingId = :fundingId
        """,
    )
    fun findByIdAndFundingIdForUpdate(
        @Param("contributionId") contributionId: Long,
        @Param("fundingId") fundingId: Long,
    ): Contribution?

    @Query(
        """
        SELECT COALESCE(SUM(contribution.amount), 0)
        FROM Contribution contribution
        WHERE contribution.fundingId = :fundingId
          AND contribution.status = :status
        """,
    )
    fun sumAmountByFundingIdAndStatus(
        @Param("fundingId") fundingId: Long,
        @Param("status") status: ContributionStatus,
    ): Long

    // 보태기가 마감되면 송금 전 기록은 금액을 빼고 메시지 전용으로 바꾼다.
    // 행마다 현재 값을 다시 읽어 갱신하므로, 그사이 '보냈어요'로 바뀐 기록은 건드리지 않는다
    @Modifying(flushAutomatically = true)
    @Query(
        """
        UPDATE Contribution contribution
        SET contribution.status = com.botem.domain.funding.entity.ContributionStatus.MESSAGE_ONLY,
            contribution.amount = 0,
            contribution.updatedAt = :now
        WHERE contribution.fundingId = :fundingId
          AND contribution.status = com.botem.domain.funding.entity.ContributionStatus.AWAITING_TRANSFER
        """,
    )
    fun convertAwaitingTransfersToMessageOnly(
        @Param("fundingId") fundingId: Long,
        @Param("now") now: LocalDateTime,
    ): Int

    fun existsByFundingIdAndStatus(
        fundingId: Long,
        status: ContributionStatus,
    ): Boolean

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
                THEN 1 ELSE 0 END), 0),
            COUNT(DISTINCT CASE
                WHEN contribution.status <> com.botem.domain.funding.entity.ContributionStatus.CANCELLED
                THEN contribution.participantUserId ELSE NULL END)
                + COALESCE(SUM(CASE
                    WHEN contribution.status <> com.botem.domain.funding.entity.ContributionStatus.CANCELLED
                        AND contribution.participantUserId IS NULL
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

    @Query(
        value = """
            WITH deduplicated_participants AS (
                SELECT
                    contribution.id AS contribution_id,
                    contribution.funding_id AS funding_id,
                    contribution.participant_user_id AS participant_user_id,
                    contribution.participant_name AS participant_name,
                    participant.profile_image_url AS profile_image_url,
                    contribution.created_at AS participated_at,
                    ROW_NUMBER() OVER (
                        PARTITION BY
                            contribution.funding_id,
                            contribution.participant_user_id,
                            CASE
                                WHEN contribution.participant_user_id IS NULL THEN contribution.id
                                ELSE 0
                            END
                        ORDER BY contribution.created_at ASC, contribution.id ASC
                    ) AS participant_rank
                FROM contributions contribution
                LEFT JOIN users participant
                    ON participant.id = contribution.participant_user_id
                WHERE contribution.funding_id IN (:fundingIds)
                  AND contribution.status <> 'CANCELLED'
            ),
            ranked_previews AS (
                SELECT
                    contribution_id,
                    funding_id,
                    participant_user_id,
                    participant_name,
                    profile_image_url,
                    ROW_NUMBER() OVER (
                        PARTITION BY funding_id
                        ORDER BY participated_at ASC, contribution_id ASC
                    ) AS preview_rank
                FROM deduplicated_participants
                WHERE participant_rank = 1
            )
            SELECT
                funding_id AS fundingId,
                participant_user_id AS participantUserId,
                participant_name AS participantName,
                profile_image_url AS profileImageUrl
            FROM ranked_previews
            WHERE preview_rank <= 3
            ORDER BY funding_id ASC, preview_rank ASC
        """,
        nativeQuery = true,
    )
    fun findParticipantPreviewsByFundingIds(
        @Param("fundingIds") fundingIds: Collection<Long>,
    ): List<FundingParticipantPreviewProjection>
}
