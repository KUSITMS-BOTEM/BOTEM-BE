package com.botem.domain.funding.dto.request

import com.botem.domain.funding.entity.RollingPaperMessage
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size

data class UpdateGuestContributionRequest(
    @field:NotBlank
    @field:Size(max = 50)
    val participantName: String,

    @field:Size(max = 460)
    val message: String? = null,

    @field:NotBlank
    @field:Size(max = 50)
    val paperTemplateCode: String,

    @field:Size(max = 50)
    val decorationCode: String? = null,

    @field:Size(max = 2048)
    val photoUrl: String? = null,

    @field:Size(max = 2048)
    val voiceUrl: String? = null,

    @field:Positive
    val voiceDurationSeconds: Int? = null,

    // 음성이 있을 때만 보낼 수 있는 자막. 길이는 서비스에서 코드 포인트로 검증하고, 여기서는 지나치게 긴 입력만 막는다
    @field:Size(max = RollingPaperMessage.MAX_VOICE_TRANSCRIPT_LENGTH * 2)
    val voiceTranscript: String? = null,
)
