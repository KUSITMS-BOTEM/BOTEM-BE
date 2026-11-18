package com.botem.domain.funding.service

import com.botem.domain.account.entity.Bank
import com.botem.domain.funding.entity.Contribution
import com.botem.domain.funding.entity.ContributionStatus
import com.botem.domain.funding.entity.Funding
import com.botem.domain.funding.entity.FundingPayoutAccountSnapshot
import com.botem.domain.funding.entity.FundingProductSnapshot
import com.botem.domain.funding.entity.FundingStatus
import com.botem.domain.funding.repository.ContributionRepository
import com.botem.domain.funding.repository.FundingRepository
import com.botem.domain.user.entity.User
import com.botem.domain.user.repository.UserRepository
import com.botem.global.config.JpaAuditingConfig
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// 처리 직후 응답의 진행률이 같은 트랜잭션에서 바뀐 상태를 반영하는지 실제 DB로 확인한다
@DataJpaTest
@Import(
    JpaAuditingConfig::class,
    DepositConfirmationService::class,
    FundingContributionCloser::class,
    DepositConfirmationPersistenceTest.FixedClockConfig::class,
)
class DepositConfirmationPersistenceTest {

    @Autowired
    private lateinit var service: DepositConfirmationService

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var fundingRepository: FundingRepository

    @Autowired
    private lateinit var contributionRepository: ContributionRepository

    @Autowired
    private lateinit var entityManager: EntityManager

    @Test
    fun `확정과 미입금 처리 결과가 응답 진행률에 바로 반영된다`() {
        val owner = userRepository.save(User(kakaoId = 1L, nickname = "지유", profileImageUrl = null))
        val funding = saveFunding(owner.id)
        val first = savePendingContribution(funding.id, 30_000L)
        val second = savePendingContribution(funding.id, 20_000L)

        val confirmed = service.confirm(owner.id, funding.id, first.id)
        assertEquals(30, confirmed.progress.confirmedPercentage)
        assertEquals(20, confirmed.progress.pendingPercentage)
        assertEquals(1L, confirmed.progress.pendingContributionCount)

        val rejected = service.reject(owner.id, funding.id, second.id)
        assertEquals(30, rejected.progress.confirmedPercentage)
        assertEquals(0, rejected.progress.pendingPercentage)
        assertEquals(0L, rejected.progress.pendingContributionCount)
    }

    @Test
    fun `전체 확정으로 목표에 닿으면 보태기를 마감하고 송금 전 참여를 0원 메시지 전용으로 바꾼다`() {
        val owner = userRepository.save(User(kakaoId = 1L, nickname = "지유", profileImageUrl = null))
        val funding = saveFunding(owner.id)
        savePendingContribution(funding.id, 60_000L)
        savePendingContribution(funding.id, 50_000L)
        val awaiting = contributionRepository.saveAndFlush(contribution(funding.id, 10_000L))

        val response = service.confirmAll(owner.id, funding.id)
        entityManager.flush()
        entityManager.clear()

        assertEquals(2, response.confirmedCount)
        assertEquals(110, response.progress.confirmedPercentage)
        assertTrue(response.progress.targetReached)
        val converted = contributionRepository.findById(awaiting.id).orElseThrow()
        assertEquals(ContributionStatus.MESSAGE_ONLY, converted.status)
        assertEquals(0L, converted.amount)
        assertEquals(FundingStatus.CLOSED, fundingRepository.findById(funding.id).orElseThrow().status)
    }

    private fun savePendingContribution(fundingId: Long, amount: Long): Contribution =
        contributionRepository.saveAndFlush(contribution(fundingId, amount).also(Contribution::markTransferSent))

    private fun contribution(fundingId: Long, amount: Long): Contribution =
        Contribution(
            fundingId = fundingId,
            participantUserId = null,
            participantName = "친구",
            amount = amount,
            status = ContributionStatus.AWAITING_TRANSFER,
            guestPasswordHash = null,
        )

    private fun saveFunding(ownerId: Long): Funding =
        fundingRepository.saveAndFlush(
            Funding.createBirthday(
                ownerId = ownerId,
                title = "지유의 생일",
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

    @TestConfiguration
    class FixedClockConfig {

        @Bean
        fun clock(): Clock = Clock.fixed(Instant.parse("2026-11-18T03:00:00Z"), ZoneId.of("Asia/Seoul"))
    }

    companion object {
        private val NOW = LocalDateTime.of(2026, 11, 18, 12, 0)
    }
}
