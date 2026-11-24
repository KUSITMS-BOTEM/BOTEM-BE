package com.botem.domain.funding.repository

import com.botem.domain.account.entity.Bank
import com.botem.domain.funding.entity.Contribution
import com.botem.domain.funding.entity.ContributionStatus
import com.botem.domain.funding.entity.Funding
import com.botem.domain.funding.entity.FundingPayoutAccountSnapshot
import com.botem.domain.funding.entity.FundingProductSnapshot
import com.botem.global.config.JpaAuditingConfig
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import

// 회원 1인 1회 검사가 취소한 보탬과 연장 전 보탬을 빼고 세는지 실제 DB로 확인한다
@DataJpaTest
@Import(JpaAuditingConfig::class)
class MemberParticipationPersistenceTest {

    @Autowired
    private lateinit var fundingRepository: FundingRepository

    @Autowired
    private lateinit var contributionRepository: ContributionRepository

    @Test
    fun `취소하지 않은 회원 보탬만 참여로 세고, 연장 시각 이후 보탬만 따로 셀 수 있다`() {
        val funding = saveFunding()
        // 생성 시각은 감사(auditing)로 실제 현재 시각이 UTC로 들어간다
        val before = LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1)
        contributionRepository.saveAndFlush(contribution(funding.id, MEMBER_ID, ContributionStatus.MESSAGE_ONLY))
        contributionRepository.saveAndFlush(
            contribution(funding.id, CANCELLED_MEMBER_ID, ContributionStatus.AWAITING_TRANSFER).also { it.cancel() },
        )

        assertTrue(exists(funding.id, MEMBER_ID))
        assertFalse(exists(funding.id, CANCELLED_MEMBER_ID))
        assertFalse(exists(funding.id, OTHER_MEMBER_ID))
        assertTrue(existsAfter(funding.id, MEMBER_ID, before))
        assertFalse(existsAfter(funding.id, MEMBER_ID, LocalDateTime.now(ZoneOffset.UTC).plusMinutes(1)))
    }

    private fun exists(fundingId: Long, userId: Long): Boolean =
        contributionRepository.existsByFundingIdAndParticipantUserIdAndStatusNot(
            fundingId,
            userId,
            ContributionStatus.CANCELLED,
        )

    private fun existsAfter(fundingId: Long, userId: Long, extendedAt: LocalDateTime): Boolean =
        contributionRepository.existsByFundingIdAndParticipantUserIdAndStatusNotAndCreatedAtAfter(
            fundingId,
            userId,
            ContributionStatus.CANCELLED,
            extendedAt,
        )

    private fun contribution(fundingId: Long, userId: Long, status: ContributionStatus): Contribution =
        Contribution(
            fundingId = fundingId,
            participantUserId = userId,
            participantName = "친구",
            amount = if (status == ContributionStatus.MESSAGE_ONLY) 0L else 10_000L,
            status = status,
            guestPasswordHash = null,
        )

    private fun saveFunding(): Funding =
        fundingRepository.saveAndFlush(
            Funding.createBirthday(
                ownerId = OWNER_ID,
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
            ),
        )

    companion object {
        private const val OWNER_ID = 1L
        private const val MEMBER_ID = 2L
        private const val CANCELLED_MEMBER_ID = 3L
        private const val OTHER_MEMBER_ID = 4L
        private val NOW = LocalDateTime.of(2026, 11, 18, 12, 0)
    }
}
