package com.botem.domain.funding.repository

import com.botem.domain.funding.entity.Funding
import org.springframework.data.jpa.repository.JpaRepository

interface FundingRepository : JpaRepository<Funding, Long>
