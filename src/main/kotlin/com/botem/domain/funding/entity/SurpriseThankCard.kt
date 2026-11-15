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
import org.hibernate.Length
import java.time.LocalDateTime

@Entity
@Table(
    name = "surprise_thank_cards",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_surprise_thank_cards_funding", columnNames = ["funding_id"]),
    ],
)
class SurpriseThankCard(
    @Column(nullable = false, updatable = false)
    val fundingId: Long,

    @Column(nullable = false, updatable = false)
    val senderUserId: Long,

    @Lob
    @Column(nullable = false, length = Length.LONG32)
    val message: String,

    @Column(nullable = false, updatable = false)
    val sentAt: LocalDateTime,
) : BaseEntity() {

    init {
        require(message.isNotBlank()) { "감사 메시지는 비어 있을 수 없습니다" }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0
}
