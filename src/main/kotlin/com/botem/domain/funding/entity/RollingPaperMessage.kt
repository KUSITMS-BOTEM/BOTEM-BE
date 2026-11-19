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
) : BaseEntity() {

    init {
        validateContent(content, paperTemplateCode, decorationCode, photoUrl, voiceUrl, voiceDurationSeconds)
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

    fun updateTextContent(content: String, paperTemplateCode: String) {
        updateContent(
            content = content,
            paperTemplateCode = paperTemplateCode,
            decorationCode = decorationCode,
            photoUrl = photoUrl,
            voiceUrl = voiceUrl,
            voiceDurationSeconds = voiceDurationSeconds,
        )
    }

    fun updateContent(
        content: String?,
        paperTemplateCode: String,
        decorationCode: String?,
        photoUrl: String?,
        voiceUrl: String?,
        voiceDurationSeconds: Int?,
    ) {
        validateContent(content, paperTemplateCode, decorationCode, photoUrl, voiceUrl, voiceDurationSeconds)
        this.content = content
        this.paperTemplateCode = paperTemplateCode
        this.decorationCode = decorationCode
        this.photoUrl = photoUrl
        this.voiceUrl = voiceUrl
        this.voiceDurationSeconds = voiceDurationSeconds
    }

    private fun validateContent(
        content: String?,
        paperTemplateCode: String,
        decorationCode: String?,
        photoUrl: String?,
        voiceUrl: String?,
        voiceDurationSeconds: Int?,
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
    }
}
