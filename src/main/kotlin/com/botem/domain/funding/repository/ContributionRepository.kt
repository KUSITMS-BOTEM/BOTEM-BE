package com.botem.domain.funding.repository

import com.botem.domain.funding.entity.Contribution
import com.botem.domain.funding.entity.ContributionStatus
import com.botem.domain.funding.repository.projection.FundingContributionSummary
import com.botem.domain.funding.repository.projection.FundingParticipantPreviewProjection
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ContributionRepository : JpaRepository<Contribution, Long> {

    fun findAllByFundingIdOrderByCreatedAtAscIdAsc(fundingId: Long): List<Contribution>

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
