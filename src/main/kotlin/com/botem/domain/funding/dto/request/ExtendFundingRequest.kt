package com.botem.domain.funding.dto.request

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

data class ExtendFundingRequest(
    val newDeadline: LocalDate,
) {
    fun newDeadlineAtEndOfDay(): LocalDateTime =
        newDeadline.atTime(LocalTime.MAX).truncatedTo(ChronoUnit.MICROS)
}
