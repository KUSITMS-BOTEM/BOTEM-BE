package com.botem.domain.funding.dto.request

import com.botem.domain.account.entity.Bank
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import java.time.LocalDate

data class CreateBirthdayFundingRequest(
    @field:Positive
    val wishId: Long,

    // 대표계좌를 사용해도 프론트가 은행과 계좌번호를 채워 보낸다
    val bank: Bank,

    @field:NotBlank
    @field:Size(max = 30)
    val accountNumber: String,

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
