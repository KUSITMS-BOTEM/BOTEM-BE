package com.botem.domain.funding.controller.docs

import com.botem.domain.funding.dto.request.CreateGiftVerificationImageUploadUrlRequest
import com.botem.domain.funding.dto.response.GiftVerificationImageUploadUrlResponse
import com.botem.global.response.ApiResponse

interface GiftVerificationControllerDocs {
    fun createImageUploadUrl(
        userId: Long,
        giftVerificationId: Long,
        request: CreateGiftVerificationImageUploadUrlRequest,
    ): ApiResponse<GiftVerificationImageUploadUrlResponse>
}
