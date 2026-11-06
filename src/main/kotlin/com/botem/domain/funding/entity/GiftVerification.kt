package com.botem.domain.funding.entity

import com.botem.global.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Lob
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDateTime

@Entity
@Table(
    name = "gift_verifications",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_gift_verifications_funding", columnNames = ["funding_id"]),
    ],
    indexes = [
        Index(name = "idx_gift_verifications_status_created_at", columnList = "status, created_at"),
    ],
)
class GiftVerification(
    @Column(nullable = false, updatable = false)
    val fundingId: Long,

    replacementProductName: String? = null,
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: GiftVerificationStatus = GiftVerificationStatus.PENDING
        protected set

    @Column(length = 2048)
    var imageUrl: String? = null
        protected set

    @Lob
    var thankYouMessage: String? = null
        protected set

    @Column(nullable = false)
    var purchasedThroughBotemLink: Boolean = false
        protected set

    @Column(length = 20)
    var replacementProductName: String? = replacementProductName
        protected set

    var completedAt: LocalDateTime? = null
        protected set

    fun complete(
        imageUrl: String,
        thankYouMessage: String?,
        purchasedThroughBotemLink: Boolean,
        completedAt: LocalDateTime,
    ) {
        this.imageUrl = imageUrl
        this.thankYouMessage = thankYouMessage
        this.purchasedThroughBotemLink = purchasedThroughBotemLink
        status = GiftVerificationStatus.COMPLETED
        this.completedAt = completedAt
    }
}
