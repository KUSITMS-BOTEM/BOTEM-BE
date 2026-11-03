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

    wishProfileKey: String = UUID.randomUUID().toString().replace("-", ""),
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

    @Column(nullable = false, unique = true, updatable = false, length = 32)
    val wishProfileKey: String = wishProfileKey

    fun updateProfile(
        nickname: String,
        birthday: LocalDate?,
    ) {
        this.nickname = nickname
        this.birthday = birthday
    }
}
