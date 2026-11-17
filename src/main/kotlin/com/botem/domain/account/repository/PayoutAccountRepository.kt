package com.botem.domain.account.repository

import com.botem.domain.account.entity.PayoutAccount
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface PayoutAccountRepository : JpaRepository<PayoutAccount, Long> {

    fun findAllByUserIdOrderByCreatedAtAsc(userId: Long): List<PayoutAccount>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT account FROM PayoutAccount account WHERE account.id = :accountId AND account.userId = :userId")
    fun findByIdAndUserIdForUpdate(
        @Param("accountId") accountId: Long,
        @Param("userId") userId: Long,
    ): PayoutAccount?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT account FROM PayoutAccount account WHERE account.userId = :userId AND account.isDefault = true")
    fun findDefaultByUserIdForUpdate(@Param("userId") userId: Long): PayoutAccount?

    fun existsByUserId(userId: Long): Boolean

    @Modifying
    @Query("DELETE FROM PayoutAccount account WHERE account.userId = :userId")
    fun deleteAllByUserId(@Param("userId") userId: Long): Int
}
