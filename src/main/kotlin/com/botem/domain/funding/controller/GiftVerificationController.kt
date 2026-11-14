package com.botem.domain.funding.controller

import com.botem.domain.funding.controller.docs.GiftVerificationControllerDocs
import com.botem.domain.funding.dto.request.CreateGiftVerificationImageUploadUrlRequest
import com.botem.domain.funding.dto.response.GiftVerificationImageUploadUrlResponse
import com.botem.domain.funding.service.GiftVerificationImageService
import com.botem.global.response.ApiResponse
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/gift-verifications")
class GiftVerificationController(
    private val giftVerificationImageService: GiftVerificationImageService,
) : GiftVerificationControllerDocs {

    @PostMapping("/{giftVerificationId}/image/upload-url")
    override fun createImageUploadUrl(
        @AuthenticationPrincipal userId: Long,
        @PathVariable giftVerificationId: Long,
        @Valid @RequestBody request: CreateGiftVerificationImageUploadUrlRequest,
    ): ApiResponse<GiftVerificationImageUploadUrlResponse> =
        ApiResponse.success(
            giftVerificationImageService.createUploadUrl(
                userId = userId,
                giftVerificationId = giftVerificationId,
                contentType = request.contentType,
                contentLength = request.contentLength,
            ),
        )
}
