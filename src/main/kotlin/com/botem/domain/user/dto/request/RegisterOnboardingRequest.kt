package com.botem.domain.user.dto.request

import com.botem.domain.account.entity.Bank
import com.fasterxml.jackson.annotation.JsonFormat
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.MonthDay

data class RegisterOnboardingRequest(
    @field:NotBlank
    val name: String,

    // 펀딩 마감일 기본값이 생일이라 비워둘 수 없음. @NotNull은 API 문서의 필수 표시용
    @field:NotNull
    @param:JsonFormat(pattern = "MM-dd")
    val birthday: MonthDay,

    val bank: Bank,

    @field:NotBlank
    @field:Size(max = 30)
    val accountNumber: String,
)
