package com.botem.domain.user.entity

import com.botem.global.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.MonthDay
import java.util.UUID

@Entity
@Table(name = "users")
class User(
    kakaoId: Long,

    nickname: String,

    profileImageUrl: String?,

    birthday: MonthDay? = null,

    name: String? = null,

    email: String? = null,

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

    @Convert(converter = MonthDayConverter::class)
    @Column(name = "birth_month_day", length = 5)
    var birthday: MonthDay? = birthday
        protected set

    @Column(length = 50)
    var name: String? = name
        protected set

    var email: String? = email
        protected set

    // 이전 버전이 사용하던 연도 포함 생일 컬럼. 탈퇴 시 개인정보를 지우기 위해서만 매핑하며, 컬럼 삭제 시 함께 제거한다.
    @Column(name = "birthday")
    private var legacyBirthday: LocalDate? = null

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
        birthday: MonthDay?,
    ) {
        this.nickname = nickname
        this.birthday = birthday
    }

    fun updateOnboardingProfile(
        name: String,
        birthday: MonthDay?,
    ) {
        this.name = name
        this.birthday = birthday
    }

    fun updateEmail(email: String?) {
        this.email = email
    }

    fun disconnectKakao() {
        kakaoConnected = false
    }

    fun withdraw(withdrawnAt: LocalDateTime) {
        kakaoId = -id
        nickname = WITHDRAWN_USER_NICKNAME
        profileImageUrl = null
        birthday = null
        name = null
        email = null
        legacyBirthday = null
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
