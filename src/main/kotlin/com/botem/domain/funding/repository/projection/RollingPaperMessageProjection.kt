package com.botem.domain.funding.repository.projection

data class RollingPaperMessageProjection(
    val messageId: Long,
    val participantName: String,
    val participantProfileImageUrl: String?,
    val content: String?,
    val paperTemplateCode: String,
    val decorationCode: String?,
    val photoUrl: String?,
    val voiceUrl: String?,
    val voiceDurationSeconds: Int?,
    val voiceTranscript: String?,
)
