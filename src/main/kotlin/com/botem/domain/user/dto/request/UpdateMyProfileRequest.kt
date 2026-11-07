package com.botem.domain.user.dto.request

import com.fasterxml.jackson.annotation.JsonFormat
import jakarta.validation.constraints.NotBlank
import java.time.MonthDay

data class UpdateMyProfileRequest(
    @field:NotBlank
    val nickname: String,

    @param:JsonFormat(pattern = "MM-dd")
    val birthday: MonthDay?,
)
