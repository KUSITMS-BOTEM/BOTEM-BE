package com.botem.domain.funding.dto.response

import com.botem.domain.funding.repository.projection.RollingPaperMessageProjection

data class RollingPaperMessageResponse(
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
) {
    companion object {
        fun from(message: RollingPaperMessageProjection): RollingPaperMessageResponse =
            RollingPaperMessageResponse(
                messageId = message.messageId,
                participantName = message.participantName,
                participantProfileImageUrl = message.participantProfileImageUrl,
                content = message.content,
                paperTemplateCode = message.paperTemplateCode,
                decorationCode = message.decorationCode,
                photoUrl = message.photoUrl,
                voiceUrl = message.voiceUrl,
                voiceDurationSeconds = message.voiceDurationSeconds,
                voiceTranscript = message.voiceTranscript,
            )
    }
}
