package com.botem.domain.user.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.PastOrPresent
import java.time.LocalDate

data class UpdateMyProfileRequest(
    @field:NotBlank
    val nickname: String,

    @field:PastOrPresent
    val birthday: LocalDate?,
)
