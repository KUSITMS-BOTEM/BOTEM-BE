package com.botem.domain.funding.repository

import com.botem.domain.funding.entity.Funding
import com.botem.domain.funding.entity.FundingStatus
import org.springframework.data.jpa.repository.JpaRepository

interface FundingRepository : JpaRepository<Funding, Long> {

    fun findAllByOwnerIdAndStatusIn(
        ownerId: Long,
        statuses: Collection<FundingStatus>,
    ): List<Funding>
}
