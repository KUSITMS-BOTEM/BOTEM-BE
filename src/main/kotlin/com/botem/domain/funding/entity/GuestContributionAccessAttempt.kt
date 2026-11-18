package com.botem.domain.funding.entity

import com.botem.global.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Duration
import java.time.LocalDateTime

@Entity
@Table(
    name = "guest_contribution_access_attempts",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_guest_contribution_access_contribution",
            columnNames = ["contribution_id"],
        ),
    ],
)
class GuestContributionAccessAttempt(
    @Column(nullable = false, updatable = false)
    val contributionId: Long,
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @Column(nullable = false)
    var failedCount: Int = 0
        protected set

    var lockedUntil: LocalDateTime? = null
        protected set

    fun isLocked(now: LocalDateTime): Boolean =
        lockedUntil?.isAfter(now) == true

    fun recordFailure(now: LocalDateTime) {
        if (lockedUntil?.isAfter(now) == false) {
            lockedUntil = null
        }

        failedCount += 1
        if (failedCount >= MAX_FAILURES) {
            failedCount = 0
            lockedUntil = now.plus(LOCK_DURATION)
        }
    }

    fun reset() {
        failedCount = 0
        lockedUntil = null
    }

    companion object {
        const val MAX_FAILURES = 5
        val LOCK_DURATION: Duration = Duration.ofMinutes(10)
    }
}
