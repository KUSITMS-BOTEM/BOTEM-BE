package com.botem.domain.funding.dto.request

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Positive
import java.time.LocalDate
import java.time.LocalDateTime

data class CreateSurpriseFundingRequest(
    @field:Positive
    val recipientId: Long,

    @field:Positive
    val wishId: Long,

    @field:Positive
    val payoutAccountId: Long? = null,

    @field:Positive
    @field:Max(10_000_000)
    val targetAmount: Long,

    val deadline: LocalDate,

    val revealAt: LocalDateTime,

    val showOnRecipientProfile: Boolean = true,

    val notifyRecipientFriends: Boolean = true,
)
