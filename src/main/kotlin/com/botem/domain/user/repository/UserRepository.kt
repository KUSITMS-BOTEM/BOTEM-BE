package com.botem.domain.user.repository

import com.botem.domain.user.entity.User
import org.springframework.data.jpa.repository.JpaRepository

interface UserRepository : JpaRepository<User, Long> {

    fun findByKakaoId(kakaoId: Long): User?
}
