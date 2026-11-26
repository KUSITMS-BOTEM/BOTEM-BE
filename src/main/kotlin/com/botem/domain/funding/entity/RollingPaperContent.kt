package com.botem.domain.funding.entity

import com.botem.global.exception.BusinessException
import com.botem.global.exception.CommonErrorCode

/**
 * 참여 경로와 무관하게 롤링페이퍼에 저장되는 콘텐츠의 정규화된 입력값이다.
 *
 * 참여자 식별, 비회원 비밀번호, 첨부 파일의 S3 소유권 검증은 각 참여 서비스가 담당한다.
 */
class RollingPaperContent private constructor(
    val message: String?,
    val paperTemplateCode: String,
    val decorationCode: String?,
    val photoUrl: String?,
    val voiceUrl: String?,
    val voiceDurationSeconds: Int?,
    val voiceTranscript: String?,
) {

    companion object {
        const val MAX_MESSAGE_LENGTH = 460
        const val MAX_PAPER_TEMPLATE_CODE_LENGTH = 50
        const val MAX_DECORATION_CODE_LENGTH = 50
        const val MAX_URL_LENGTH = 2048
        const val MAX_VOICE_TRANSCRIPT_LENGTH = 500

        fun from(
            message: String?,
            paperTemplateCode: String,
            decorationCode: String?,
            photoUrl: String?,
            voiceUrl: String?,
            voiceDurationSeconds: Int?,
            voiceTranscript: String?,
        ): RollingPaperContent {
            val normalizedMessage = message.normalizeOptional(MAX_MESSAGE_LENGTH, "축하 메시지는 460자 이하여야 합니다.")
            val normalizedPaperTemplateCode = paperTemplateCode.normalizeRequired(
                MAX_PAPER_TEMPLATE_CODE_LENGTH,
                "편지지를 선택해야 합니다.",
            )
            val normalizedDecorationCode = decorationCode.normalizeOptional(
                MAX_DECORATION_CODE_LENGTH,
                "스티커 코드는 50자 이하여야 합니다.",
            )
            val normalizedPhotoUrl = photoUrl.normalizeOptional(MAX_URL_LENGTH, "사진 URL은 2048자 이하여야 합니다.")
            val normalizedVoiceUrl = voiceUrl.normalizeOptional(MAX_URL_LENGTH, "음성 URL은 2048자 이하여야 합니다.")
            val normalizedVoiceTranscript = voiceTranscript.normalizeOptional(
                MAX_VOICE_TRANSCRIPT_LENGTH * 2,
                "음성 자막은 ${MAX_VOICE_TRANSCRIPT_LENGTH}자 이하여야 합니다.",
            )

            if (
                normalizedMessage == null &&
                normalizedDecorationCode == null &&
                normalizedPhotoUrl == null &&
                normalizedVoiceUrl == null
            ) {
                throw invalidInput("축하 메시지, 스티커, 사진 또는 음성 중 하나는 필수입니다.")
            }
            if ((normalizedVoiceUrl == null) != (voiceDurationSeconds == null) || voiceDurationSeconds?.let { it <= 0 } == true) {
                throw invalidInput("음성 URL과 0초보다 긴 재생 시간을 함께 입력해야 합니다.")
            }
            if (normalizedVoiceTranscript != null && normalizedVoiceUrl == null) {
                throw invalidInput("음성 자막은 음성과 함께 입력해야 합니다.")
            }
            if (
                normalizedVoiceTranscript != null &&
                normalizedVoiceTranscript.codePointCount(0, normalizedVoiceTranscript.length) > MAX_VOICE_TRANSCRIPT_LENGTH
            ) {
                throw invalidInput("음성 자막은 ${MAX_VOICE_TRANSCRIPT_LENGTH}자 이하여야 합니다.")
            }

            return RollingPaperContent(
                message = normalizedMessage,
                paperTemplateCode = normalizedPaperTemplateCode,
                decorationCode = normalizedDecorationCode,
                photoUrl = normalizedPhotoUrl,
                voiceUrl = normalizedVoiceUrl,
                voiceDurationSeconds = voiceDurationSeconds,
                voiceTranscript = normalizedVoiceTranscript,
            )
        }

        private fun String?.normalizeOptional(maxLength: Int, detail: String): String? {
            val normalized = this?.trim { it.isWhitespace() || Character.isSpaceChar(it) }
                ?.takeIf(String::isNotEmpty)
            if (normalized != null && normalized.length > maxLength) {
                throw invalidInput(detail)
            }
            return normalized
        }

        private fun String.normalizeRequired(maxLength: Int, detail: String): String =
            normalizeOptional(maxLength, detail) ?: throw invalidInput(detail)

        private fun invalidInput(detail: String) = BusinessException(CommonErrorCode.INVALID_INPUT_VALUE, detail)
    }
}
