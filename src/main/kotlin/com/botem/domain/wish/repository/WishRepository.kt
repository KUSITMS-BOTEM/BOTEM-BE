package com.botem.domain.wish.repository

import com.botem.domain.wish.entity.Wish
import org.springframework.data.jpa.repository.JpaRepository

interface WishRepository : JpaRepository<Wish, Long> {

    fun findAllByUserIdOrderByCreatedAtDesc(userId: Long): List<Wish>

    fun existsByUserIdAndRecommendedProductId(userId: Long, recommendedProductId: Long): Boolean
}
