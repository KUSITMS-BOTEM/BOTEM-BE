package com.botem.domain.funding.repository

import com.botem.domain.funding.entity.GuestContributionAccessAttempt
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface GuestContributionAccessAttemptRepository : JpaRepository<GuestContributionAccessAttempt, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        """
        SELECT attempt
        FROM GuestContributionAccessAttempt attempt
        WHERE attempt.contributionId = :contributionId
        """,
    )
    fun findByContributionIdForUpdate(
        @Param("contributionId") contributionId: Long,
    ): GuestContributionAccessAttempt?
}
