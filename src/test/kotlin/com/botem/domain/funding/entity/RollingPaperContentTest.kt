package com.botem.domain.funding.entity

import com.botem.global.exception.BusinessException
import com.botem.global.exception.CommonErrorCode
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class RollingPaperContentTest {

    @Test
    fun `콘텐츠 입력을 정규화하고 저장한다`() {
        val content = RollingPaperContent.from(
            message = "  생일 축하해!  ",
            paperTemplateCode = "  BLUE_DOT_GRID  ",
            decorationCode = "  HEART  ",
            photoUrl = "  https://example.com/photo.jpg  ",
            voiceUrl = "  https://example.com/voice.mp3  ",
            voiceDurationSeconds = 12,
            voiceTranscript = "  축하해  ",
        )

        assertEquals("생일 축하해!", content.message)
        assertEquals("BLUE_DOT_GRID", content.paperTemplateCode)
        assertEquals("HEART", content.decorationCode)
        assertEquals("https://example.com/photo.jpg", content.photoUrl)
        assertEquals("https://example.com/voice.mp3", content.voiceUrl)
        assertEquals(12, content.voiceDurationSeconds)
        assertEquals("축하해", content.voiceTranscript)
    }

    @Test
    fun `텍스트 외에 스티커 사진 또는 음성만으로 콘텐츠를 만들 수 있다`() {
        val decoration = content(decorationCode = "HEART")
        val photo = content(photoUrl = "https://example.com/photo.jpg")
        val voice = content(voiceUrl = "https://example.com/voice.mp3", voiceDurationSeconds = 12)

        assertEquals("HEART", decoration.decorationCode)
        assertEquals("https://example.com/photo.jpg", photo.photoUrl)
        assertEquals("https://example.com/voice.mp3", voice.voiceUrl)
        assertNull(voice.message)
    }

    @Test
    fun `내용과 편지지 음성 자막의 관계를 검증한다`() {
        assertInvalidInput { content(message = " ") }
        assertInvalidInput { content(paperTemplateCode = " ") }
        assertInvalidInput { content(voiceUrl = "https://example.com/voice.mp3") }
        assertInvalidInput { content(voiceDurationSeconds = 12) }
        assertInvalidInput { content(voiceUrl = "https://example.com/voice.mp3", voiceDurationSeconds = 0) }
        assertInvalidInput { content(voiceTranscript = "음성 없는 자막") }
    }

    @Test
    fun `콘텐츠 길이와 자막 코드 포인트 경계를 검증한다`() {
        val content = RollingPaperContent.from(
            message = "가".repeat(RollingPaperContent.MAX_MESSAGE_LENGTH),
            paperTemplateCode = "P".repeat(RollingPaperContent.MAX_PAPER_TEMPLATE_CODE_LENGTH),
            decorationCode = "D".repeat(RollingPaperContent.MAX_DECORATION_CODE_LENGTH),
            photoUrl = "h".repeat(RollingPaperContent.MAX_URL_LENGTH),
            voiceUrl = null,
            voiceDurationSeconds = null,
            voiceTranscript = null,
        )

        assertEquals(RollingPaperContent.MAX_MESSAGE_LENGTH, content.message?.length)
        assertInvalidInput { content(message = "가".repeat(RollingPaperContent.MAX_MESSAGE_LENGTH + 1)) }
        assertInvalidInput {
            content(
                voiceUrl = "https://example.com/voice.mp3",
                voiceDurationSeconds = 12,
                voiceTranscript = "🎉".repeat(RollingPaperContent.MAX_VOICE_TRANSCRIPT_LENGTH + 1),
            )
        }
    }

    private fun content(
        message: String? = null,
        paperTemplateCode: String = "BLUE_DOT_GRID",
        decorationCode: String? = null,
        photoUrl: String? = null,
        voiceUrl: String? = null,
        voiceDurationSeconds: Int? = null,
        voiceTranscript: String? = null,
    ): RollingPaperContent =
        RollingPaperContent.from(
            message = message,
            paperTemplateCode = paperTemplateCode,
            decorationCode = decorationCode,
            photoUrl = photoUrl,
            voiceUrl = voiceUrl,
            voiceDurationSeconds = voiceDurationSeconds,
            voiceTranscript = voiceTranscript,
        )

    private fun assertInvalidInput(block: () -> Unit) {
        val exception = assertFailsWith<BusinessException>(block = block)
        assertEquals(CommonErrorCode.INVALID_INPUT_VALUE, exception.errorCode)
    }
}
