package com.botem.domain.auth.repository

import com.botem.domain.auth.entity.RefreshToken
import org.springframework.data.jpa.repository.JpaRepository

interface RefreshTokenRepository : JpaRepository<RefreshToken, Long> {

    fun findByUserId(userId: Long): RefreshToken?
}
