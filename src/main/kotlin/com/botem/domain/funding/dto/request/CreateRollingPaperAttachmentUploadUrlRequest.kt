package com.botem.domain.funding.dto.request

import com.botem.domain.funding.entity.RollingPaperAttachmentType
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

data class CreateRollingPaperAttachmentUploadUrlRequest(
    @field:NotNull
    val attachmentType: RollingPaperAttachmentType,

    @field:NotBlank
    val contentType: String,

    @field:NotNull
    val contentLength: Long,
)
