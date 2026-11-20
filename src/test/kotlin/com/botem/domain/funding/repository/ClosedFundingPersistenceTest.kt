package com.botem.domain.funding.repository

import com.botem.domain.account.entity.Bank
import com.botem.domain.funding.entity.Funding
import com.botem.domain.funding.entity.FundingPayoutAccountSnapshot
import com.botem.domain.funding.entity.FundingProductSnapshot
import com.botem.domain.funding.entity.FundingStatus
import com.botem.domain.funding.entity.FundingType
import com.botem.global.config.JpaAuditingConfig
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import java.time.LocalDateTime
import kotlin.test.assertEquals

@DataJpaTest
@Import(JpaAuditingConfig::class)
class ClosedFundingPersistenceTest {

    @Autowired
    private lateinit var fundingRepository: FundingRepository

    @Test
    fun `내가 연 펀딩 중 마감된 펀딩만 마감일 최신순으로 조회한다`() {
        val deadlinePassed = save(birthday(deadline = NOW.minusDays(3)))
        val deadlineNow = save(birthday(deadline = NOW))
        val earlyClosed = save(birthday(deadline = NOW.plusDays(5)).also { it.closeEarly(NOW.minusHours(1)) })
        // 목표를 달성해 마감 전에 결과를 연 펀딩도 마감된 펀딩으로 본다
        val earlyOpened = save(birthday(deadline = NOW.plusDays(3)).also { it.openResult(NOW.minusHours(1), earlyOpenable = true) })
        val revealedSurprise = save(surprise(deadline = NOW.minusDays(1), revealAt = NOW.minusMinutes(1)))
        save(birthday(deadline = NOW.plusMinutes(1)))
        save(surprise(deadline = NOW.minusDays(1), revealAt = NOW.plusMinutes(1)))
        save(birthday(deadline = NOW.minusDays(1)).also { setField(it, "status", FundingStatus.CANCELLED) })
        save(birthday(deadline = NOW.minusDays(1), ownerId = OTHER_OWNER_ID))

        val closed = fundingRepository.findClosedFundingsByOwnerId(OWNER_ID, NOW)

        assertEquals(
            listOf(earlyClosed.id, earlyOpened.id, deadlineNow.id, revealedSurprise.id, deadlinePassed.id),
            closed.map(Funding::id),
        )
    }

    private fun save(funding: Funding): Funding = fundingRepository.saveAndFlush(funding)

    private fun birthday(deadline: LocalDateTime, ownerId: Long = OWNER_ID): Funding =
        funding(ownerId, null, FundingType.BIRTHDAY, deadline, null)

    private fun surprise(deadline: LocalDateTime, revealAt: LocalDateTime): Funding =
        funding(OWNER_ID, RECIPIENT_ID, FundingType.SURPRISE, deadline, revealAt)
            .also { setField(it, "status", FundingStatus.WAITING_REVEAL) }

    private fun funding(
        ownerId: Long,
        recipientId: Long?,
        type: FundingType,
        deadline: LocalDateTime,
        revealAt: LocalDateTime?,
    ): Funding =
        Funding(
            ownerId = ownerId,
            recipientId = recipientId,
            type = type,
            title = null,
            targetAmount = 100_000L,
            deadline = deadline,
            revealAt = revealAt,
            story = null,
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
                encryptedAccountNumber = "encrypted",
                accountHolder = null,
            ),
        )

    private fun setField(target: Any, fieldName: String, value: Any) {
        target.javaClass.getDeclaredField(fieldName).apply {
            isAccessible = true
            set(target, value)
        }
    }

    companion object {
        private const val OWNER_ID = 1L
        private const val OTHER_OWNER_ID = 2L
        private const val RECIPIENT_ID = 3L
        private val NOW = LocalDateTime.of(2026, 11, 15, 9, 41)
    }
}
