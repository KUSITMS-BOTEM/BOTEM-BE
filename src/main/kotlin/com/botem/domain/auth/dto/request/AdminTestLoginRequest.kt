package com.botem.domain.auth.dto.request

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class AdminTestLoginRequest(
    @field:NotBlank
    @field:Email
    @field:Size(max = 255)
    val email: String,
)
