package com.botem.domain.user.dto.request

import jakarta.validation.constraints.NotBlank

data class CreateProfileImageUploadUrlRequest(
    @field:NotBlank
    val contentType: String,
)
