package com.botem.domain.auth.repository

import com.botem.domain.auth.entity.RefreshToken
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock

interface RefreshTokenRepository : JpaRepository<RefreshToken, Long> {

    // 같은 Refresh 토큰으로 동시에 재발급을 요청해도 한 요청만 통과하도록 행을 잠금
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findByUserId(userId: Long): RefreshToken?
}
