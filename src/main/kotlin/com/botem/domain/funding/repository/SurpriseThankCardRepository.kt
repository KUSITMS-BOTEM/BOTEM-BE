package com.botem.domain.funding.repository

import com.botem.domain.funding.entity.SurpriseThankCard
import org.springframework.data.jpa.repository.JpaRepository

interface SurpriseThankCardRepository : JpaRepository<SurpriseThankCard, Long> {

    fun findByFundingId(fundingId: Long): SurpriseThankCard?

    fun existsByFundingId(fundingId: Long): Boolean
}
