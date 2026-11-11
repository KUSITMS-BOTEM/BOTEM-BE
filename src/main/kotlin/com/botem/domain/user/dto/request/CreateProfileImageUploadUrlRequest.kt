package com.botem.domain.user.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

data class CreateProfileImageUploadUrlRequest(
    @field:NotBlank
    val contentType: String,

    // 업로드할 파일의 바이트 크기. @NotNull은 API 문서의 필수 표시용
    @field:NotNull
    val contentLength: Long,
)
