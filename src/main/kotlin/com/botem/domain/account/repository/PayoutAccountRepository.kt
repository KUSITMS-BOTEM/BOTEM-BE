package com.botem.domain.account.repository

import com.botem.domain.account.entity.PayoutAccount
import org.springframework.data.jpa.repository.JpaRepository

interface PayoutAccountRepository : JpaRepository<PayoutAccount, Long> {

    fun findAllByUserIdOrderByCreatedAtAsc(userId: Long): List<PayoutAccount>
}
