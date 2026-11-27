package com.botem.domain.user.repository

import com.botem.domain.user.entity.User
import com.botem.domain.user.entity.UserStatus
import jakarta.persistence.LockModeType
import org.springframework.cache.annotation.Cacheable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface UserRepository : JpaRepository<User, Long> {

    fun findByKakaoId(kakaoId: Long): User?

    fun findAllByKakaoIdInAndStatus(kakaoIds: Collection<Long>, status: UserStatus): List<User>

    fun findByWishProfileKey(wishProfileKey: String): User?

    // 인증된 요청마다 호출되므로 활성 회원만 캐시한다. 상태는 활성 → 탈퇴로만 바뀌고, 탈퇴가 커밋되면 지운다
    // (UserStatusCacheEvictor). 지우기 직전에 읽은 값이 다시 들어가는 드문 경우도 TTL(5분) 뒤에는 사라진다
    @Cacheable(
        cacheNames = [USER_STATUS_CACHE],
        key = "#p0",
        unless = "#result != T(com.botem.domain.user.entity.UserStatus).ACTIVE",
    )
    @Query("SELECT u.status FROM User u WHERE u.id = :userId")
    fun findStatusById(@Param("userId") userId: Long): UserStatus?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :userId")
    fun findByIdForUpdate(@Param("userId") userId: Long): User?

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

const val USER_STATUS_CACHE = "userStatus"
