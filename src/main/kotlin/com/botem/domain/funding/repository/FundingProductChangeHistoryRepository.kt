package com.botem.domain.funding.repository

import com.botem.domain.funding.entity.FundingProductChangeHistory
import org.springframework.data.jpa.repository.JpaRepository

interface FundingProductChangeHistoryRepository : JpaRepository<FundingProductChangeHistory, Long>
