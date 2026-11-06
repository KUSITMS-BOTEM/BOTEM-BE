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
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(
    name = "contributions",
    indexes = [
        Index(name = "idx_contributions_funding_status", columnList = "funding_id, status"),
        Index(name = "idx_contributions_participant_user", columnList = "participant_user_id, created_at"),
    ],
)
class Contribution(
    @Column(nullable = false, updatable = false)
    val fundingId: Long,

    @Column(updatable = false)
    val participantUserId: Long?,

    participantName: String,

    @Column(nullable = false, updatable = false)
    val amount: Long,

    status: ContributionStatus,

    @Column(updatable = false, length = 255)
    val guestPasswordHash: String?,
) : BaseEntity() {

    init {
        require(status != ContributionStatus.CONFIRMED) {
            "확인 완료 상태는 confirm을 통해서만 설정할 수 있습니다"
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @Column(nullable = false, length = 50)
    var participantName: String = participantName
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var status: ContributionStatus = status
        protected set

    var confirmedAt: LocalDateTime? = null
        protected set

    fun updateParticipantName(participantName: String) {
        this.participantName = participantName
    }

    fun markTransferSent() {
        status = ContributionStatus.PENDING_CONFIRMATION
    }

    fun confirm(confirmedAt: LocalDateTime) {
        status = ContributionStatus.CONFIRMED
        this.confirmedAt = confirmedAt
    }

    fun reject() {
        status = ContributionStatus.REJECTED
        confirmedAt = null
    }

    fun cancel() {
        status = ContributionStatus.CANCELLED
        confirmedAt = null
    }
}
