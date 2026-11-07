package com.botem.domain.user.dto.request

import com.botem.domain.account.entity.Bank
import com.fasterxml.jackson.annotation.JsonFormat
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.MonthDay

data class RegisterOnboardingRequest(
    @field:NotBlank
    val name: String,

    @param:JsonFormat(pattern = "MM-dd")
    val birthday: MonthDay?,

    val bank: Bank,

    @field:NotBlank
    @field:Size(max = 30)
    val accountNumber: String,
)
