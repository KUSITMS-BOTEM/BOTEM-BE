package com.botem.domain.user.entity

import com.botem.global.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "users")
class User(
    kakaoId: Long,

    nickname: String,

    profileImageUrl: String?,

    birthday: LocalDate? = null,

    wishProfileKey: String? = generateWishProfileKey(),

    kakaoConnected: Boolean = true,

    status: UserStatus = UserStatus.ACTIVE,

    withdrawnAt: LocalDateTime? = null,
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @Column(nullable = false, unique = true)
    var kakaoId: Long = kakaoId
        protected set

    @Column(nullable = false)
    var nickname: String = nickname
        protected set

    var profileImageUrl: String? = profileImageUrl
        protected set

    var birthday: LocalDate? = birthday
        protected set

    @Column(unique = true, length = 32)
    var wishProfileKey: String? = wishProfileKey
        protected set

    @Column(nullable = false)
    var kakaoConnected: Boolean = kakaoConnected
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: UserStatus = status
        protected set

    var withdrawnAt: LocalDateTime? = withdrawnAt
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

    fun withdraw(withdrawnAt: LocalDateTime) {
        kakaoId = -id
        nickname = WITHDRAWN_USER_NICKNAME
        profileImageUrl = null
        birthday = null
        wishProfileKey = null
        kakaoConnected = false
        status = UserStatus.WITHDRAWN
        this.withdrawnAt = withdrawnAt
    }

    companion object {
        private const val WITHDRAWN_USER_NICKNAME = "탈퇴한 사용자"

        fun generateWishProfileKey(): String = UUID.randomUUID().toString().replace("-", "")
    }
}
