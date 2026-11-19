package com.botem.domain.funding.service

import com.botem.domain.account.entity.Bank
import com.botem.domain.funding.entity.Contribution
import com.botem.domain.funding.entity.ContributionStatus
import com.botem.domain.funding.entity.Funding
import com.botem.domain.funding.entity.FundingPayoutAccountSnapshot
import com.botem.domain.funding.entity.FundingProductSnapshot
import com.botem.domain.funding.entity.FundingStatus
import com.botem.domain.funding.entity.FundingType
import com.botem.domain.funding.exception.FundingErrorCode
import com.botem.domain.funding.repository.ContributionRepository
import com.botem.domain.funding.repository.FundingRepository
import com.botem.domain.user.entity.User
import com.botem.domain.user.repository.UserRepository
import com.botem.global.config.JpaAuditingConfig
import com.botem.global.exception.BusinessException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

// 서비스 호출마다 커밋·롤백되는지 확인해야 하므로 테스트 트랜잭션 밖에서 실행한다
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import(
    JpaAuditingConfig::class,
    SurpriseGiftPreparationService::class,
    FundingContributionCloser::class,
    SurpriseGiftPreparationPersistenceTest.FixedClockConfig::class,
)
class SurpriseGiftPreparationPersistenceTest {

    @Autowired
    private lateinit var service: SurpriseGiftPreparationService

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var fundingRepository: FundingRepository

    @Autowired
    private lateinit var contributionRepository: ContributionRepository

    @AfterEach
    fun tearDown() {
        contributionRepository.deleteAll()
        fundingRepository.deleteAll()
        userRepository.deleteAll()
    }

    @Test
    fun `확인 대기로 준비를 거부해도 보태기 마감은 커밋되고, 제외하고 완료하면 공개 카드 대상이 된다`() {
        val owner = userRepository.save(User(kakaoId = 1L, nickname = "민지", profileImageUrl = null))
        val recipient = userRepository.save(User(kakaoId = 2L, nickname = "지유", profileImageUrl = null))
        val funding = saveSurprise(owner.id, recipient.id)
        val awaiting = saveContribution(funding.id, 10_000L)
        val pending = saveContribution(funding.id, 20_000L, ContributionStatus.PENDING_CONFIRMATION)
        saveContribution(funding.id, 30_000L, ContributionStatus.CONFIRMED)

        val exception = assertFailsWith<BusinessException> {
            service.prepare(owner.id, funding.id, excludePendingContributions = false)
        }
        assertEquals(FundingErrorCode.PENDING_CONTRIBUTION_EXISTS, exception.errorCode)
        val rejectedFunding = fundingRepository.findById(funding.id).orElseThrow()
        assertEquals(FundingStatus.CLOSED, rejectedFunding.status)
        assertNull(rejectedFunding.giftPreparedAt)
        assertEquals(
            ContributionStatus.MESSAGE_ONLY,
            contributionRepository.findById(awaiting.id).orElseThrow().status,
        )
        assertEquals(emptyList(), fundingRepository.findRevealedSurpriseCards(recipient.id, NOW))

        val response = service.prepare(owner.id, funding.id, excludePendingContributions = true)

        assertEquals(30_000L, response.confirmedAmount)
        assertEquals("지유", response.recipientName)
        val preparedFunding = fundingRepository.findById(funding.id).orElseThrow()
        assertEquals(FundingStatus.WAITING_REVEAL, preparedFunding.status)
        assertEquals(NOW, preparedFunding.giftPreparedAt)
        assertEquals(
            ContributionStatus.PENDING_CONFIRMATION,
            contributionRepository.findById(pending.id).orElseThrow().status,
        )
        assertEquals(
            listOf(funding.id),
            fundingRepository.findRevealedSurpriseCards(recipient.id, NOW).map { it.fundingId },
        )
    }

    private fun saveContribution(
        fundingId: Long,
        amount: Long,
        status: ContributionStatus = ContributionStatus.AWAITING_TRANSFER,
    ): Contribution {
        val contribution = Contribution(
            fundingId = fundingId,
            participantUserId = null,
            participantName = "친구",
            amount = amount,
            status = ContributionStatus.AWAITING_TRANSFER,
            guestPasswordHash = null,
        )
        if (status != ContributionStatus.AWAITING_TRANSFER) {
            contribution.markTransferSent()
        }
        if (status == ContributionStatus.CONFIRMED) {
            contribution.confirm(NOW.minusDays(2))
        }

        return contributionRepository.saveAndFlush(contribution)
    }

    private fun saveSurprise(ownerId: Long, recipientId: Long): Funding =
        fundingRepository.saveAndFlush(
            Funding(
                ownerId = ownerId,
                recipientId = recipientId,
                type = FundingType.SURPRISE,
                title = null,
                targetAmount = 100_000L,
                deadline = NOW.minusDays(1),
                revealAt = NOW.minusHours(12),
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
                    encryptedAccountNumber = "encrypted-account-number",
                    accountHolder = "민지",
                ),
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
