package com.botem.domain.user.entity

import com.botem.global.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "users")
class User(
    @Column(nullable = false, unique = true)
    val kakaoId: Long,

    nickname: String,

    profileImageUrl: String?,

    birthday: LocalDate? = null,

    wishProfileKey: String? = generateWishProfileKey(),

    kakaoConnected: Boolean = true,
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @Column(nullable = false)
    var nickname: String = nickname
        protected set

    var profileImageUrl: String? = profileImageUrl
        protected set

    var birthday: LocalDate? = birthday
        protected set

    // 이전 버전과 함께 실행되는 배포 동안 이전 버전이 생성한 NULL을 허용한다.
    // 모든 이전 버전 종료 후 후속 마이그레이션에서 NOT NULL로 전환한다.
    @Column(unique = true, updatable = false, length = 32)
    val wishProfileKey: String? = wishProfileKey

    @Column(nullable = false)
    var kakaoConnected: Boolean = kakaoConnected
        protected set

    fun updateProfile(
        nickname: String,
        birthday: LocalDate?,
    ) {
        this.nickname = nickname
        this.birthday = birthday
    }

    fun disconnectKakao() {
        kakaoConnected = false
    }

    companion object {
        fun generateWishProfileKey(): String = UUID.randomUUID().toString().replace("-", "")
    }
}
