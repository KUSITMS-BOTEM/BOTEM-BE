package com.botem.domain.funding.entity

import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SurpriseMessageEntityTest {

    @Test
    fun `롤링페이퍼는 텍스트 외에 사진이나 음성만으로도 생성할 수 있다`() {
        val text = RollingPaperMessage(1L, "축하해!", "LIME_DOT_GRID")
        val photo = RollingPaperMessage(
            contributionId = 2L,
            content = null,
            paperTemplateCode = "BLUE_DOT_GRID",
            photoUrl = "https://image.example.com/photo.jpg",
        )
        val voice = RollingPaperMessage(
            contributionId = 3L,
            content = null,
            paperTemplateCode = "BLACK_CONFETTI",
            voiceUrl = "https://cdn.example.com/voice.m4a",
            voiceDurationSeconds = 12,
        )

        assertEquals("축하해!", text.content)
        assertEquals("https://image.example.com/photo.jpg", photo.photoUrl)
        assertEquals(12, voice.voiceDurationSeconds)
    }

    @Test
    fun `내용이 없거나 음성 정보가 불완전한 롤링페이퍼는 생성할 수 없다`() {
        assertFailsWith<IllegalArgumentException> {
            RollingPaperMessage(1L, null, "LIME_DOT_GRID")
        }
        assertFailsWith<IllegalArgumentException> {
            RollingPaperMessage(
                contributionId = 1L,
                content = "축하해!",
                paperTemplateCode = "LIME_DOT_GRID",
                voiceUrl = "https://cdn.example.com/voice.m4a",
            )
        }
        assertFailsWith<IllegalArgumentException> {
            RollingPaperMessage(
                contributionId = 1L,
                content = "축하해!",
                paperTemplateCode = "LIME_DOT_GRID",
                voiceUrl = "https://cdn.example.com/voice.m4a",
                voiceDurationSeconds = 0,
            )
        }
    }

    @Test
    fun `감사 카드는 빈 메시지로 생성할 수 없다`() {
        val sentAt = LocalDateTime.of(2026, 11, 15, 9, 42)
        val thankCard = SurpriseThankCard(1L, 2L, "정말 고마워!", sentAt)

        assertEquals("정말 고마워!", thankCard.message)
        assertFailsWith<IllegalArgumentException> {
            SurpriseThankCard(1L, 2L, " ", sentAt)
        }
    }
}
