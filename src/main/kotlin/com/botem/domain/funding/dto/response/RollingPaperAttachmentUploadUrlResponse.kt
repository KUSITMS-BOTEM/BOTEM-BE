package com.botem.domain.funding.dto.response

import com.botem.domain.funding.entity.RollingPaperAttachmentType

data class RollingPaperAttachmentUploadUrlResponse(
    val attachmentType: RollingPaperAttachmentType,
    val uploadUrl: String,
    val fileUrl: String,
)
