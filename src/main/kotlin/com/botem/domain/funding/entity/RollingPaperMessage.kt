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

    @Column(length = 50)
    val decorationCode: String? = null,

    @Column(length = 2048)
    val photoUrl: String? = null,

    @Column(length = 2048)
    val voiceUrl: String? = null,

    val voiceDurationSeconds: Int? = null,
) : BaseEntity() {

    init {
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

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @Lob
    var content: String? = content
        protected set

    @Column(nullable = false, length = 50)
    var paperTemplateCode: String = paperTemplateCode
        protected set

    fun updateTextContent(content: String, paperTemplateCode: String) {
        this.content = content
        this.paperTemplateCode = paperTemplateCode
    }
}
