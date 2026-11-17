package com.botem.domain.funding.dto.request

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import java.time.LocalDate

data class CreateBirthdayFundingRequest(
    @field:Positive
    val wishId: Long,

    @field:Positive
    val payoutAccountId: Long? = null,

    @field:NotBlank
    @field:Size(max = 20)
    val title: String,

    @field:Positive
    @field:Max(10_000_000)
    val targetAmount: Long,

    val deadline: LocalDate,

    @field:NotBlank
    @field:Size(max = 300)
    val story: String,

    @field:Size(max = 300)
    val promise: String? = null,
)
