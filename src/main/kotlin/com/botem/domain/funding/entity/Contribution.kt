package com.botem.domain.funding.entity

import com.botem.domain.funding.exception.FundingErrorCode
import com.botem.global.entity.BaseEntity
import com.botem.global.exception.BusinessException
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Duration
import java.time.LocalDateTime

@Entity
@Table(
    name = "contributions",
    indexes = [
        Index(name = "idx_contributions_funding_status", columnList = "funding_id, status"),
        Index(name = "idx_contributions_participant_user", columnList = "participant_user_id, created_at"),
    ],
    uniqueConstraints = [
        UniqueConstraint(name = "uk_contributions_funding_request_key", columnNames = ["funding_id", "request_key"]),
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

    @Column(updatable = false, length = 64)
    val requestKey: String? = null,
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

    @Column(nullable = false)
    var guestPasswordFailedCount: Int = 0
        protected set

    var guestPasswordLockedUntil: LocalDateTime? = null
        protected set

    fun isGuestPasswordLocked(now: LocalDateTime): Boolean =
        guestPasswordLockedUntil?.isAfter(now) == true

    // 4자리 비밀번호의 무작위 대입을 막기 위해 연속으로 틀리면 일정 시간 잠근다
    fun recordGuestPasswordFailure(now: LocalDateTime) {
        guestPasswordFailedCount += 1
        if (guestPasswordFailedCount >= MAX_GUEST_PASSWORD_FAILURES) {
            guestPasswordFailedCount = 0
            guestPasswordLockedUntil = now.plus(GUEST_PASSWORD_LOCK_DURATION)
        }
    }

    fun resetGuestPasswordFailures() {
        guestPasswordFailedCount = 0
        guestPasswordLockedUntil = null
    }

    fun updateParticipantName(participantName: String) {
        this.participantName = participantName
    }

    // 같은 요청이 다시 와도 결과가 같도록 이미 보낸 상태면 그대로 둔다
    fun markTransferSent() {
        when (status) {
            ContributionStatus.AWAITING_TRANSFER -> status = ContributionStatus.PENDING_CONFIRMATION
            ContributionStatus.PENDING_CONFIRMATION, ContributionStatus.CONFIRMED -> Unit
            ContributionStatus.MESSAGE_ONLY ->
                throw BusinessException(FundingErrorCode.CONTRIBUTION_CONVERTED_TO_MESSAGE_ONLY)
            else -> throw BusinessException(FundingErrorCode.CONTRIBUTION_TRANSFER_NOT_MARKABLE)
        }
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

    companion object {
        // ponytail: 기획 미확정 값. 정책이 정해지면 이 두 값만 바꾼다
        const val MAX_GUEST_PASSWORD_FAILURES = 5
        val GUEST_PASSWORD_LOCK_DURATION: Duration = Duration.ofMinutes(10)
    }
}
