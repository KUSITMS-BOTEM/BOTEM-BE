package com.botem.domain.account.entity

import com.botem.global.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "payout_accounts")
class PayoutAccount(
    @Column(nullable = false, updatable = false)
    val userId: Long,

    bank: Bank,

    encryptedAccountNumber: String,

    accountHolder: String?,

    isDefault: Boolean,
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var bank: Bank = bank
        protected set

    @Column(nullable = false, length = 255)
    var encryptedAccountNumber: String = encryptedAccountNumber
        protected set

    @Column(length = 100)
    var accountHolder: String? = accountHolder
        protected set

    @Column(nullable = false)
    var isDefault: Boolean = isDefault
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var verificationStatus: AccountVerificationStatus = AccountVerificationStatus.UNVERIFIED
        protected set

    fun update(
        bank: Bank,
        encryptedAccountNumber: String,
        accountHolder: String?,
    ) {
        this.bank = bank
        this.encryptedAccountNumber = encryptedAccountNumber
        this.accountHolder = accountHolder
        verificationStatus = AccountVerificationStatus.UNVERIFIED
    }

    fun designateAsDefault() {
        isDefault = true
    }

    fun releaseDefault() {
        isDefault = false
    }
}
