package com.botem.domain.funding.repository

import com.botem.domain.funding.entity.ContributionThankCard
import org.springframework.data.jpa.repository.JpaRepository

interface ContributionThankCardRepository : JpaRepository<ContributionThankCard, Long> {

    fun findByContributionId(contributionId: Long): ContributionThankCard?

    fun findByShareKey(shareKey: String): ContributionThankCard?
}
