package com.botem.domain.funding.repository

import com.botem.domain.funding.entity.GiftVerification
import org.springframework.data.jpa.repository.JpaRepository

interface GiftVerificationRepository : JpaRepository<GiftVerification, Long> {

    fun findByFundingId(fundingId: Long): GiftVerification?
}
