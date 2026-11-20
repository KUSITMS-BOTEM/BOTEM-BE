package com.botem.domain.funding.entity

import com.botem.global.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Lob
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "rolling_paper_messages",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_rolling_paper_messages_contribution", columnNames = ["contribution_id"]),
    ],
)
class RollingPaperMessage(
    @Column(nullable = false, updatable = false)
    val contributionId: Long,

    content: String? = null,

    paperTemplateCode: String,

    decorationCode: String? = null,

    photoUrl: String? = null,

    voiceUrl: String? = null,

    voiceDurationSeconds: Int? = null,

    voiceTranscript: String? = null,
) : BaseEntity() {

    init {
        validateContent(content, paperTemplateCode, decorationCode, photoUrl, voiceUrl, voiceDurationSeconds, voiceTranscript)
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @Lob
    var content: String? = content
        protected set

    @Column(nullable = false, length = 50)
    var paperTemplateCode: String = paperTemplateCode
        protected set

    @Column(length = 50)
    var decorationCode: String? = decorationCode
        protected set

    @Column(length = 2048)
    var photoUrl: String? = photoUrl
        protected set

    @Column(length = 2048)
    var voiceUrl: String? = voiceUrl
        protected set

    var voiceDurationSeconds: Int? = voiceDurationSeconds
        protected set

    // 음성 아래에 보여주는 자막. 프론트가 만들어 보내고 서버는 길이만 검증해 저장한다
    @Column(length = MAX_VOICE_TRANSCRIPT_LENGTH)
    var voiceTranscript: String? = voiceTranscript
        protected set

    fun updateTextContent(content: String, paperTemplateCode: String) {
        updateContent(
            content = content,
            paperTemplateCode = paperTemplateCode,
            decorationCode = decorationCode,
            photoUrl = photoUrl,
            voiceUrl = voiceUrl,
            voiceDurationSeconds = voiceDurationSeconds,
            voiceTranscript = voiceTranscript,
        )
    }

    fun updateContent(
        content: String?,
        paperTemplateCode: String,
        decorationCode: String?,
        photoUrl: String?,
        voiceUrl: String?,
        voiceDurationSeconds: Int?,
        voiceTranscript: String?,
    ) {
        validateContent(content, paperTemplateCode, decorationCode, photoUrl, voiceUrl, voiceDurationSeconds, voiceTranscript)
        this.content = content
        this.paperTemplateCode = paperTemplateCode
        this.decorationCode = decorationCode
        this.photoUrl = photoUrl
        this.voiceUrl = voiceUrl
        this.voiceDurationSeconds = voiceDurationSeconds
        this.voiceTranscript = voiceTranscript
    }

    private fun validateContent(
        content: String?,
        paperTemplateCode: String,
        decorationCode: String?,
        photoUrl: String?,
        voiceUrl: String?,
        voiceDurationSeconds: Int?,
        voiceTranscript: String?,
    ) {
        require(!content.isNullOrBlank() || decorationCode != null || photoUrl != null || voiceUrl != null) {
            "롤링페이퍼에는 메시지, 꾸미기, 사진 또는 음성 중 하나가 필요합니다"
        }
        require(paperTemplateCode.isNotBlank()) { "편지지 템플릿 코드는 비어 있을 수 없습니다" }
        require(voiceDurationSeconds?.let { it > 0 } != false) {
            "음성 메시지 길이는 0보다 커야 합니다"
        }
        require((voiceUrl == null) == (voiceDurationSeconds == null)) {
            "음성 메시지 URL과 재생 시간은 함께 입력해야 합니다"
        }
        require(voiceTranscript == null || voiceUrl != null) { "음성 자막은 음성 메시지가 있을 때만 입력할 수 있습니다" }
        require(voiceTranscript == null || voiceTranscript.length <= MAX_VOICE_TRANSCRIPT_LENGTH) {
            "음성 자막은 ${MAX_VOICE_TRANSCRIPT_LENGTH}자 이하여야 합니다"
        }
    }

    companion object {
        const val MAX_VOICE_TRANSCRIPT_LENGTH = 500
    }
}
