package com.botem.domain.account.repository

import com.botem.domain.account.entity.PayoutAccount
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.data.domain.Pageable

interface PayoutAccountRepository : JpaRepository<PayoutAccount, Long> {

    fun findAllByUserIdOrderByCreatedAtAsc(userId: Long): List<PayoutAccount>

    fun findFirstByUserIdAndIsDefaultTrue(userId: Long): PayoutAccount?

    @Query(
        "SELECT account.id FROM PayoutAccount account WHERE account.id > :afterAccountId ORDER BY account.id ASC",
    )
    fun findIdsAfter(
        @Param("afterAccountId") afterAccountId: Long,
        pageable: Pageable,
    ): List<Long>

    @Query("SELECT account.userId FROM PayoutAccount account WHERE account.id IN :accountIds")
    fun findUserIdsByIdIn(@Param("accountIds") accountIds: Collection<Long>): List<Long>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT account FROM PayoutAccount account WHERE account.id IN :accountIds ORDER BY account.id ASC")
    fun findAllByIdInForUpdate(@Param("accountIds") accountIds: Collection<Long>): List<PayoutAccount>

    fun existsByUserId(userId: Long): Boolean

    @Modifying
    @Query("DELETE FROM PayoutAccount account WHERE account.userId = :userId")
    fun deleteAllByUserId(@Param("userId") userId: Long): Int
}
