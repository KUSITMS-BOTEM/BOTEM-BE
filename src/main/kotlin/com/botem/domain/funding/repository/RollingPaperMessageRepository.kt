package com.botem.domain.funding.repository

import com.botem.domain.funding.entity.RollingPaperMessage
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
}
