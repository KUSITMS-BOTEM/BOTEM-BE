package com.botem.domain.auth.entity

import com.botem.global.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

// ponytail: 사용자당 Refresh 토큰 1개라 다른 기기에서 로그인하면 이전 기기의 Refresh 토큰은 무효화됨, 다중 기기 지원 시 기기별 저장으로 변경
@Entity
@Table(name = "refresh_tokens")
class RefreshToken(
    @Column(nullable = false, unique = true)
    val userId: Long,

    token: String,
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @Column(nullable = false, length = 512)
    var token: String = token
        protected set

    fun rotate(token: String) {
        this.token = token
    }
}
