package com.botem.domain.funding.repository

import com.botem.domain.account.entity.Bank
import com.botem.domain.funding.entity.Funding
import com.botem.domain.funding.entity.FundingPayoutAccountSnapshot
import com.botem.domain.funding.entity.FundingProductSnapshot
import com.botem.global.config.JpaAuditingConfig
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

// 펀딩마다 서로 다른 공유 키가 저장되고, 공유 키로만 펀딩 ID를 찾는지 실제 DB로 확인한다
@DataJpaTest
@Import(JpaAuditingConfig::class)
class FundingShareKeyPersistenceTest {

    @Autowired
    private lateinit var fundingRepository: FundingRepository

    @Test
    fun `펀딩마다 다른 공유 키를 저장하고 공유 키로 펀딩 ID를 찾는다`() {
        val first = fundingRepository.saveAndFlush(birthdayFunding())
        val second = fundingRepository.saveAndFlush(birthdayFunding())

        assertTrue(Funding.isShareKeyFormat(first.shareKey))
        assertNotEquals(first.shareKey, second.shareKey)
        assertEquals(first.id, fundingRepository.findIdByShareKey(first.shareKey))
        assertEquals(second.id, fundingRepository.findIdByShareKey(second.shareKey))
        assertNull(fundingRepository.findIdByShareKey("ffffffffffffffffffffffffffffffff"))
    }

    private fun birthdayFunding(): Funding =
        Funding.createBirthday(
            ownerId = 1L,
            targetAmount = 100_000L,
            deadline = NOW.plusDays(3),
            story = "새 헤드폰이 필요해요",
            promise = null,
            product = FundingProductSnapshot(
                sourceWishId = null,
                name = "무선 헤드폰",
                imageUrl = "https://image.example.com/product.jpg",
                price = 100_000L,
                url = "https://shop.example.com/product",
            ),
            payoutAccount = FundingPayoutAccountSnapshot(
                sourcePayoutAccountId = null,
                bank = Bank.KAKAO_BANK,
                encryptedAccountNumber = "encrypted-account-number",
                accountHolder = "지유",
            ),
            now = NOW,
        )

    companion object {
        private val NOW = LocalDateTime.of(2026, 11, 20, 12, 0)
    }
}
