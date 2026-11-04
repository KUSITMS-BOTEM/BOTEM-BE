package com.botem.domain.user.repository

import com.botem.domain.user.entity.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface UserRepository : JpaRepository<User, Long> {

    fun findByKakaoId(kakaoId: Long): User?

    @Modifying(flushAutomatically = true)
    @Query(
        value = """
            UPDATE users
            SET wish_profile_key = :wishProfileKey
            WHERE id = :userId AND wish_profile_key IS NULL
        """,
        nativeQuery = true,
    )
    fun updateWishProfileKeyIfNull(
        @Param("userId") userId: Long,
        @Param("wishProfileKey") wishProfileKey: String,
    ): Int

    @Query(
        value = "SELECT wish_profile_key FROM users WHERE id = :userId FOR UPDATE",
        nativeQuery = true,
    )
    fun findWishProfileKeyForUpdate(@Param("userId") userId: Long): String?
}
