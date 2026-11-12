package com.botem.domain.user.entity

import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.MonthDay
import kotlin.test.assertEquals

class BirthdayDdayTest {

    @Test
    fun `오늘이 생일이면 0, 지난 생일은 내년 생일까지 남은 일수를 계산한다`() {
        val today = LocalDate.of(2026, 11, 12)

        assertEquals(0, MonthDay.of(11, 12).daysUntilNextBirthday(today))
        assertEquals(364, MonthDay.of(11, 11).daysUntilNextBirthday(today))
    }

    @Test
    fun `평년의 2월 29일 생일은 2월 28일로 계산한다`() {
        assertEquals(1, MonthDay.of(2, 29).daysUntilNextBirthday(LocalDate.of(2027, 2, 27)))
    }
}
