package com.botem.domain.wish.repository

import com.botem.domain.wish.entity.Wish
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface WishRepository : JpaRepository<Wish, Long> {

    fun findAllByUserIdOrderByCreatedAtDesc(userId: Long): List<Wish>

    fun findAllByUserId(userId: Long, pageable: Pageable): Page<Wish>

    fun findByIdAndUserId(id: Long, userId: Long): Wish?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT wish FROM Wish wish WHERE wish.id = :wishId AND wish.userId = :userId")
    fun findByIdAndUserIdForUpdate(
        @Param("wishId") wishId: Long,
        @Param("userId") userId: Long,
    ): Wish?

    fun existsByUserIdAndRecommendedProductId(userId: Long, recommendedProductId: Long): Boolean
}
