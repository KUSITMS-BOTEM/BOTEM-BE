package com.botem.domain.user.entity

import java.time.LocalDate
import java.time.MonthDay
import java.time.temporal.ChronoUnit

// 다음 생일까지 남은 일수. 오늘이 생일이면 0이고, 평년의 2월 29일 생일은 2월 28일로 계산한다
fun MonthDay.daysUntilNextBirthday(today: LocalDate): Long {
    var nextBirthday = atYear(today.year)
    if (nextBirthday.isBefore(today)) {
        nextBirthday = atYear(today.year + 1)
    }

    return ChronoUnit.DAYS.between(today, nextBirthday)
}
