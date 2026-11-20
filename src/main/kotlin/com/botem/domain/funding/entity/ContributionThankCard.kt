package com.botem.domain.funding.entity

import com.botem.global.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(
    name = "contribution_thank_cards",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_contribution_thank_cards_contribution",
            columnNames = ["contribution_id"],
        ),
        UniqueConstraint(
            name = "uk_contribution_thank_cards_share_key",
            columnNames = ["share_key"],
        ),
    ],
)
class ContributionThankCard(
    @Column(nullable = false, updatable = false)
    val contributionId: Long,

    @Column(nullable = false, updatable = false)
    val senderUserId: Long,

    @Column(nullable = false, length = MAX_MESSAGE_LENGTH)
    val message: String,

    @Column(nullable = false, updatable = false, length = SHARE_KEY_LENGTH)
    val shareKey: String = generateShareKey(),

    @Column(nullable = false, updatable = false)
    val sentAt: LocalDateTime,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    val paperColor: ThankCardColor = ThankCardColor.LIME,
) : BaseEntity() {

    init {
        require(message.isNotBlank()) { "감사 메시지는 비어 있을 수 없습니다" }
        require(hasValidMessageLength(message)) { "감사 메시지는 ${MAX_MESSAGE_LENGTH}자 이하여야 합니다" }
        require(SHARE_KEY_REGEX.matches(shareKey)) { "감사 카드 공유 키 형식이 올바르지 않습니다" }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    companion object {
        const val MAX_MESSAGE_LENGTH = 50
        private const val SHARE_KEY_LENGTH = 32
        private val SHARE_KEY_REGEX = Regex("[0-9a-f]{$SHARE_KEY_LENGTH}")

        fun generateShareKey(): String = UUID.randomUUID().toString().replace("-", "")

        fun hasValidMessageLength(message: String): Boolean =
            message.codePointCount(0, message.length) <= MAX_MESSAGE_LENGTH
    }
}
