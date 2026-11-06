package com.botem.domain.account.repository

import com.botem.domain.account.entity.PayoutAccount
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface PayoutAccountRepository : JpaRepository<PayoutAccount, Long> {

    fun findAllByUserIdOrderByCreatedAtAsc(userId: Long): List<PayoutAccount>

    @Modifying
    @Query("DELETE FROM PayoutAccount account WHERE account.userId = :userId")
    fun deleteAllByUserId(@Param("userId") userId: Long): Int
}
