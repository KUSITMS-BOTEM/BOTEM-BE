package com.botem.domain.funding.entity

import com.botem.domain.account.entity.Bank
import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated

@Embeddable
class FundingPayoutAccountSnapshot(
    @Column(name = "source_payout_account_id")
    val sourcePayoutAccountId: Long?,

    @Enumerated(EnumType.STRING)
    @Column(name = "payout_bank", nullable = false, length = 30)
    val bank: Bank,

    @Column(name = "payout_encrypted_account_number", nullable = false, length = 255)
    val encryptedAccountNumber: String,

    @Column(name = "payout_account_holder", length = 100)
    val accountHolder: String?,
)
