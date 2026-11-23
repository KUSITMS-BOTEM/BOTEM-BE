package com.botem.domain.funding.repository

import com.botem.domain.account.entity.Bank
import com.botem.domain.funding.entity.Funding
import com.botem.domain.funding.entity.FundingPayoutAccountSnapshot
import com.botem.domain.funding.entity.FundingProductSnapshot
import com.botem.domain.funding.entity.FundingStatus
import com.botem.domain.funding.entity.FundingType
import com.botem.domain.funding.repository.projection.ProfileFundingTypeProjection
import com.botem.global.config.JpaAuditingConfig
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import java.time.LocalDateTime
import kotlin.test.assertEquals

@DataJpaTest
@Import(JpaAuditingConfig::class)
class FriendProfileFundingPersistenceTest {

    @Autowired
    private lateinit var fundingRepository: FundingRepository

    @Test
    fun `위시 프로필에는 진행 중인 본인 생일 펀딩과 노출 동의한 공개 전 서프라이즈만 마감 임박 순으로 조회한다`() {
        val birthday = save(birthday(deadline = NOW.plusDays(5)))
        val earlyClosedBirthday = save(birthday(deadline = NOW.plusDays(9)).also { it.closeEarly(NOW) })
        val surprise = save(surprise(deadline = NOW.plusDays(2), showOnRecipientProfile = true))
        save(birthday(deadline = NOW))
        save(birthday(deadline = NOW.plusDays(1)).also { setField(it, "status", FundingStatus.CANCELLED) })
        save(surprise(deadline = NOW.plusDays(3), showOnRecipientProfile = false))
        save(surprise(deadline = NOW.minusDays(1), showOnRecipientProfile = true, revealAt = NOW))
        save(surprise(deadline = NOW.plusDays(4), showOnRecipientProfile = true, ownerId = PROFILE_USER_ID, recipientId = OTHER_USER_ID))
        save(birthday(deadline = NOW.plusDays(6), ownerId = OTHER_USER_ID))

        val withSurprise = fundingRepository.findProfileFundings(PROFILE_USER_ID, NOW, true)
        val withoutSurprise = fundingRepository.findProfileFundings(PROFILE_USER_ID, NOW, false)

        assertEquals(listOf(surprise.id, birthday.id, earlyClosedBirthday.id), withSurprise.map(Funding::id))
        assertEquals(listOf(birthday.id, earlyClosedBirthday.id), withoutSurprise.map(Funding::id))
        assertEquals(
            setOf(
                ProfileFundingTypeProjection(PROFILE_USER_ID, FundingType.BIRTHDAY),
                ProfileFundingTypeProjection(PROFILE_USER_ID, FundingType.SURPRISE),
                ProfileFundingTypeProjection(OTHER_USER_ID, FundingType.BIRTHDAY),
            ),
            fundingRepository.findProfileFundingTypes(listOf(PROFILE_USER_ID, OTHER_USER_ID), NOW).toSet(),
        )
    }

    @Test
    fun `진행 중 펀딩 수는 일반 펀딩의 마감일과 서프라이즈의 공개 시각을 각각 기준으로 센다`() {
        save(birthday(deadline = NOW.plusDays(1)))
        save(birthday(deadline = NOW))
        save(surprise(deadline = NOW.plusDays(1), showOnRecipientProfile = true, ownerId = PROFILE_USER_ID))
        save(surprise(deadline = NOW.minusDays(1), showOnRecipientProfile = true, revealAt = NOW, ownerId = PROFILE_USER_ID))

        assertEquals(2L, fundingRepository.countActiveFundings(PROFILE_USER_ID, NOW))
    }

    private fun save(funding: Funding): Funding = fundingRepository.saveAndFlush(funding)

    private fun birthday(deadline: LocalDateTime, ownerId: Long = PROFILE_USER_ID): Funding =
        funding(ownerId, null, FundingType.BIRTHDAY, deadline, null, false)

    private fun surprise(
        deadline: LocalDateTime,
        showOnRecipientProfile: Boolean,
        revealAt: LocalDateTime = deadline.plusSeconds(1),
        ownerId: Long = OTHER_USER_ID,
        recipientId: Long = PROFILE_USER_ID,
    ): Funding =
        funding(ownerId, recipientId, FundingType.SURPRISE, deadline, revealAt, showOnRecipientProfile)
            .also { setField(it, "status", FundingStatus.WAITING_REVEAL) }

    private fun funding(
        ownerId: Long,
        recipientId: Long?,
        type: FundingType,
        deadline: LocalDateTime,
        revealAt: LocalDateTime?,
        showOnRecipientProfile: Boolean,
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
            showOnRecipientProfile = showOnRecipientProfile,
        )

    private fun setField(target: Any, fieldName: String, value: Any) {
        target.javaClass.getDeclaredField(fieldName).apply {
            isAccessible = true
            set(target, value)
        }
    }

    companion object {
        private const val PROFILE_USER_ID = 1L
        private const val OTHER_USER_ID = 2L
        private val NOW = LocalDateTime.of(2026, 10, 21, 9, 41)
    }
}
