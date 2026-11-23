package com.botem.domain.funding.repository.projection

interface ArchiveRollingPaperSummaryProjection {
    val fundingId: Long
    val messageCount: Long
    val writerName: String
    val writerProfileImageUrl: String?
    val content: String?
    val paperTemplateCode: String
    val voiceUrl: String?
}
