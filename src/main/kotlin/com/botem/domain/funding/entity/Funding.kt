package com.botem.domain.funding.entity

import com.botem.global.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(
    name = "fundings",
    indexes = [
        Index(name = "idx_fundings_owner_status_deadline", columnList = "owner_id, status, deadline"),
        Index(name = "idx_fundings_recipient_type_reveal", columnList = "recipient_id, funding_type, reveal_at"),
    ],
)
class Funding(
    @Column(nullable = false, updatable = false)
    val ownerId: Long,

    @Column(updatable = false)
    val recipientId: Long?,

    type: FundingType,

    title: String,

    targetAmount: Long,

    deadline: LocalDateTime,

    revealAt: LocalDateTime?,

    story: String?,

    promise: String?,

    product: FundingProductSnapshot,

    payoutAccount: FundingPayoutAccountSnapshot,
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @Enumerated(EnumType.STRING)
    @Column(name = "funding_type", nullable = false, updatable = false, length = 20)
    val type: FundingType = type

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var status: FundingStatus = FundingStatus.FUNDING
        protected set

    @Column(nullable = false, length = 20)
    var title: String = title
        protected set

    @Column(nullable = false)
    var targetAmount: Long = targetAmount
        protected set

    @Column(nullable = false)
    var deadline: LocalDateTime = deadline
        protected set

    var revealAt: LocalDateTime? = revealAt
        protected set

    @Column(length = 300)
    var story: String? = story
        protected set

    @Column(length = 300)
    var promise: String? = promise
        protected set

    @Embedded
    var product: FundingProductSnapshot = product
        protected set

    @Embedded
    val payoutAccount: FundingPayoutAccountSnapshot = payoutAccount

    @Column(nullable = false)
    var extensionCount: Int = 0
        protected set

    var extendedAt: LocalDateTime? = null
        protected set

    var resultOpenedAt: LocalDateTime? = null
        protected set

    var giftPreparedAt: LocalDateTime? = null
        protected set

    var completedAt: LocalDateTime? = null
        protected set
}
