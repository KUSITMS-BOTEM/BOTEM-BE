package com.botem.domain.funding.controller

import com.botem.domain.funding.controller.docs.RollingPaperAttachmentControllerDocs
import com.botem.domain.funding.dto.request.CreateRollingPaperAttachmentUploadUrlRequest
import com.botem.domain.funding.dto.response.RollingPaperAttachmentUploadUrlResponse
import com.botem.domain.funding.service.RollingPaperAttachmentService
import com.botem.global.response.ApiResponse
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/fundings/{fundingId}/rolling-paper-attachments")
class RollingPaperAttachmentController(
    private val rollingPaperAttachmentService: RollingPaperAttachmentService,
) : RollingPaperAttachmentControllerDocs {

    @PostMapping("/upload-url")
    override fun createUploadUrl(
        @AuthenticationPrincipal userId: Long?,
        @PathVariable fundingId: Long,
        @Valid @RequestBody request: CreateRollingPaperAttachmentUploadUrlRequest,
    ): ApiResponse<RollingPaperAttachmentUploadUrlResponse> =
        ApiResponse.success(
            rollingPaperAttachmentService.createUploadUrl(
                userId = userId,
                fundingId = fundingId,
                attachmentType = request.attachmentType,
                contentType = request.contentType,
                contentLength = request.contentLength,
            ),
        )
}
