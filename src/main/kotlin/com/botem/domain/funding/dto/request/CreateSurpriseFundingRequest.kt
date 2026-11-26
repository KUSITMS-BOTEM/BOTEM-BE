package com.botem.domain.funding.dto.request

import com.botem.domain.account.entity.Bank
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import java.time.LocalDate

data class CreateSurpriseFundingRequest(
    @field:Positive
    val recipientId: Long,

    @field:Positive
    val wishId: Long,

    // 일반 펀딩과 같이 대표계좌를 사용해도 프론트가 은행과 계좌번호를 채워 보낸다
    val bank: Bank,

    @field:NotBlank
    @field:Size(max = 30)
    val accountNumber: String,

    @field:Positive
    @field:Max(10_000_000)
    val targetAmount: Long,

    val deadline: LocalDate,

    val revealAt: LocalDate,

    val showOnRecipientProfile: Boolean = true,

    val notifyRecipientFriends: Boolean = true,

    // 친구들이 참고할 1인 추천 금액. 정하지 않거나 친구에게 보여주지 않으면 생략(null)
    @field:Positive
    @field:Max(10_000_000)
    val recommendedAmount: Long? = null,
)
