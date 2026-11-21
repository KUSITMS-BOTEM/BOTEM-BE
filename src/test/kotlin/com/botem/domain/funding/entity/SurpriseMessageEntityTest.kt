package com.botem.domain.funding.entity

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class SurpriseMessageEntityTest {

    @Test
    fun `롤링페이퍼는 텍스트 외에 사진이나 음성만으로도 생성할 수 있다`() {
        val text = RollingPaperMessage(1L, "축하해!", "LIME_DOT_GRID")
        val decoration = RollingPaperMessage(
            contributionId = 4L,
            content = null,
            paperTemplateCode = "PINK_GRID",
            decorationCode = "PARTY_POPPER",
        )
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
        assertEquals(1L, text.contributionId)
        assertEquals(0L, text.id)
        assertEquals("PARTY_POPPER", decoration.decorationCode)
        assertEquals("https://image.example.com/photo.jpg", photo.photoUrl)
        assertEquals(12, voice.voiceDurationSeconds)
    }

    @Test
    fun `내용이 없거나 음성 정보가 불완전한 롤링페이퍼는 생성할 수 없다`() {
        assertFailsWith<IllegalArgumentException> {
            RollingPaperMessage(1L, null, "LIME_DOT_GRID")
        }
        assertFailsWith<IllegalArgumentException> {
            RollingPaperMessage(1L, "축하해!", " ")
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
    fun `롤링페이퍼 첨부를 교체하고 텍스트 수정에서는 기존 첨부를 유지한다`() {
        val message = RollingPaperMessage(
            contributionId = 1L,
            content = "축하해!",
            paperTemplateCode = "LIME_DOT_GRID",
            photoUrl = "https://image.example.com/old.jpg",
        )

        message.updateContent(
            content = null,
            paperTemplateCode = "BLACK_CONFETTI",
            decorationCode = "HEART",
            photoUrl = null,
            voiceUrl = "https://cdn.example.com/voice.m4a",
            voiceDurationSeconds = 12,
            voiceTranscript = "생일 축하해",
        )

        assertNull(message.content)
        assertNull(message.photoUrl)
        assertEquals("HEART", message.decorationCode)
        assertEquals(12, message.voiceDurationSeconds)
        assertEquals("생일 축하해", message.voiceTranscript)

        message.updateTextContent("새 메시지", "BLUE_DOT_GRID")
        assertEquals("새 메시지", message.content)
        assertEquals("https://cdn.example.com/voice.m4a", message.voiceUrl)
        assertEquals("생일 축하해", message.voiceTranscript)
    }

    @Test
    fun `수정 후에도 롤링페이퍼 내용 규칙을 지킨다`() {
        val message = RollingPaperMessage(1L, "축하해!", "LIME_DOT_GRID")

        assertFailsWith<IllegalArgumentException> {
            message.updateContent(null, "LIME_DOT_GRID", null, null, null, null, null)
        }
        assertFailsWith<IllegalArgumentException> {
            message.updateContent("축하해!", " ", null, null, null, null, null)
        }
        assertFailsWith<IllegalArgumentException> {
            message.updateContent("축하해!", "LIME_DOT_GRID", null, null, "voice.m4a", null, null)
        }
    }

    @Test
    fun `음성 자막은 음성이 있을 때만 500자까지 저장할 수 있다`() {
        assertFailsWith<IllegalArgumentException> {
            RollingPaperMessage(1L, "축하해!", "LIME_DOT_GRID", voiceTranscript = "음성 없는 자막")
        }
        assertFailsWith<IllegalArgumentException> {
            RollingPaperMessage(
                contributionId = 1L,
                paperTemplateCode = "LIME_DOT_GRID",
                voiceUrl = "https://cdn.example.com/voice.m4a",
                voiceDurationSeconds = 12,
                voiceTranscript = "가".repeat(RollingPaperMessage.MAX_VOICE_TRANSCRIPT_LENGTH + 1),
            )
        }

        val message = RollingPaperMessage(
            contributionId = 1L,
            paperTemplateCode = "LIME_DOT_GRID",
            voiceUrl = "https://cdn.example.com/voice.m4a",
            voiceDurationSeconds = 12,
            voiceTranscript = "가".repeat(RollingPaperMessage.MAX_VOICE_TRANSCRIPT_LENGTH),
        )
        assertEquals(RollingPaperMessage.MAX_VOICE_TRANSCRIPT_LENGTH, message.voiceTranscript?.length)
    }

}
