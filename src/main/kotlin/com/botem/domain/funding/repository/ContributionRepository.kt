package com.botem.domain.funding.repository

import com.botem.domain.funding.entity.Contribution
import org.springframework.data.jpa.repository.JpaRepository

interface ContributionRepository : JpaRepository<Contribution, Long> {

    fun findAllByFundingIdOrderByCreatedAtAsc(fundingId: Long): List<Contribution>
}
