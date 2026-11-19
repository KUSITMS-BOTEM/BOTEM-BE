package com.botem.domain.funding.entity

import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ContributionThankCardTest {

    @Test
    fun `공유 키를 자동 생성하고 이모지 50자 메시지를 허용한다`() {
        val message = "😀".repeat(ContributionThankCard.MAX_MESSAGE_LENGTH)

        val thankCard = ContributionThankCard(
            contributionId = 1L,
            senderUserId = 2L,
            message = message,
            sentAt = NOW,
        )

        assertEquals(message, thankCard.message)
        assertTrue(thankCard.shareKey.matches(Regex("[0-9a-f]{32}")))
    }

    @Test
    fun `공백이거나 50자를 넘는 메시지는 거부한다`() {
        listOf(" ", "😀".repeat(ContributionThankCard.MAX_MESSAGE_LENGTH + 1)).forEach { message ->
            assertFailsWith<IllegalArgumentException> {
                ContributionThankCard(1L, 2L, message, sentAt = NOW)
            }
        }
    }

    @Test
    fun `공유 키 형식이 올바르지 않으면 거부한다`() {
        assertFailsWith<IllegalArgumentException> {
            ContributionThankCard(1L, 2L, "고마워", shareKey = "invalid", sentAt = NOW)
        }
    }

    companion object {
        private val NOW = LocalDateTime.of(2026, 11, 19, 20, 0)
    }
}
