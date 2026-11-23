package com.botem.domain.funding.repository

import com.botem.domain.funding.entity.RollingPaperMessage
import com.botem.domain.funding.repository.projection.ArchiveRollingPaperSummaryProjection
import com.botem.domain.funding.repository.projection.RollingPaperMessageProjection
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface RollingPaperMessageRepository : JpaRepository<RollingPaperMessage, Long> {

    fun findByContributionId(contributionId: Long): RollingPaperMessage?

    fun findAllByContributionIdIn(contributionIds: Collection<Long>): List<RollingPaperMessage>

    @Query(
        """
        SELECT new com.botem.domain.funding.repository.projection.RollingPaperMessageProjection(
            message.id,
            contribution.participantName,
            participant.profileImageUrl,
            message.content,
            message.paperTemplateCode,
            message.decorationCode,
            message.photoUrl,
            message.voiceUrl,
            message.voiceDurationSeconds,
            message.voiceTranscript
        )
        FROM RollingPaperMessage message
        JOIN Contribution contribution ON contribution.id = message.contributionId
        LEFT JOIN User participant ON participant.id = contribution.participantUserId
        WHERE contribution.fundingId = :fundingId
          AND contribution.status <> com.botem.domain.funding.entity.ContributionStatus.CANCELLED
        ORDER BY contribution.createdAt ASC, contribution.id ASC
        """,
    )
    fun findAllByFundingId(@Param("fundingId") fundingId: Long): List<RollingPaperMessageProjection>

    @Query(
        """
        SELECT COUNT(message)
        FROM RollingPaperMessage message
        JOIN Contribution contribution ON contribution.id = message.contributionId
        WHERE contribution.fundingId = :fundingId
          AND contribution.status <> com.botem.domain.funding.entity.ContributionStatus.CANCELLED
        """,
    )
    fun countByFundingId(@Param("fundingId") fundingId: Long): Long

    // 카드에는 최신 유효 메시지 하나만 보이지만, 메시지 수는 같은 조회에서 함께 계산한다.
    // MySQL 8과 H2 모두 지원하는 윈도 함수로 펀딩별 대표 메시지를 고른다.
    @Query(
        value = """
            WITH ranked_messages AS (
                SELECT
                    contribution.funding_id AS funding_id,
                    COUNT(message.id) OVER (PARTITION BY contribution.funding_id) AS message_count,
                    contribution.participant_name AS writer_name,
                    participant.profile_image_url AS writer_profile_image_url,
                    message.content AS content,
                    message.paper_template_code AS paper_template_code,
                    -- MySQL은 TRUE/FALSE 식을 정수로 돌려줘 Boolean 프로젝션으로 바꿀 수 없으므로 원본 값을 넘기고 서비스에서 판단한다
                    message.voice_url AS voice_url,
                    ROW_NUMBER() OVER (
                        PARTITION BY contribution.funding_id
                        ORDER BY contribution.created_at DESC, contribution.id DESC
                    ) AS message_rank
                FROM rolling_paper_messages message
                JOIN contributions contribution ON contribution.id = message.contribution_id
                LEFT JOIN users participant ON participant.id = contribution.participant_user_id
                WHERE contribution.funding_id IN (:fundingIds)
                  AND contribution.status <> 'CANCELLED'
            )
            SELECT
                funding_id AS fundingId,
                message_count AS messageCount,
                writer_name AS writerName,
                writer_profile_image_url AS writerProfileImageUrl,
                content AS content,
                paper_template_code AS paperTemplateCode,
                voice_url AS voiceUrl
            FROM ranked_messages
            WHERE message_rank = 1
            """,
        nativeQuery = true,
    )
    fun findArchiveSummariesByFundingIds(
        @Param("fundingIds") fundingIds: Collection<Long>,
    ): List<ArchiveRollingPaperSummaryProjection>
}
