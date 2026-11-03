package com.botem.domain.user.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.PastOrPresent
import jakarta.validation.constraints.Size
import java.time.LocalDate

data class UpdateMyProfileRequest(
    @field:NotBlank
    @field:Size(max = 255)
    val nickname: String,

    @field:PastOrPresent
    val birthday: LocalDate?,
)
