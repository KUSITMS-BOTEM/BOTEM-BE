package com.botem.domain.funding.service

import com.botem.domain.account.entity.Bank
import com.botem.domain.funding.entity.Contribution
import com.botem.domain.funding.entity.ContributionStatus
import com.botem.domain.funding.entity.Funding
import com.botem.domain.funding.entity.FundingPayoutAccountSnapshot
import com.botem.domain.funding.entity.FundingProductSnapshot
import com.botem.domain.funding.entity.FundingStatus
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
import kotlin.test.assertTrue

// 마감 처리의 일괄 전환 쿼리와 결과 공개 흐름을 실제 DB로 확인한다.
// 서비스 호출마다 커밋·롤백되는지 확인해야 하므로 테스트 트랜잭션 밖에서 실행한다
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import(
    JpaAuditingConfig::class,
    FundingResultService::class,
    FundingContributionCloser::class,
    FundingResultPersistenceTest.FixedClockConfig::class,
)
class FundingResultPersistenceTest {

    @Autowired
    private lateinit var service: FundingResultService

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
    fun `마감 후 결과를 조회하면 송금 전 참여만 0원 메시지 전용으로 바뀐다`() {
        val owner = userRepository.save(User(kakaoId = 1L, nickname = "지유", profileImageUrl = null))
        val funding = saveFunding(owner.id)
        val awaiting = saveContribution(funding.id, 10_000L)
        val pending = saveContribution(funding.id, 20_000L, ContributionStatus.PENDING_CONFIRMATION)
        saveContribution(funding.id, 30_000L, ContributionStatus.CONFIRMED)

        val result = service.getResult(owner.id, funding.id)

        val converted = contributionRepository.findById(awaiting.id).orElseThrow()
        assertEquals(ContributionStatus.MESSAGE_ONLY, converted.status)
        assertEquals(0L, converted.amount)
        assertEquals(
            ContributionStatus.PENDING_CONFIRMATION,
            contributionRepository.findById(pending.id).orElseThrow().status,
        )
        assertEquals(FundingStatus.CLOSED, fundingRepository.findById(funding.id).orElseThrow().status)
        assertEquals(30_000L, result.confirmedAmount)
        assertEquals(1L, result.pendingContributionCount)
        assertTrue(result.pendingCleanupRequired)

        val exception = assertFailsWith<BusinessException> { converted.markTransferSent() }
        assertEquals(FundingErrorCode.CONTRIBUTION_CONVERTED_TO_MESSAGE_ONLY, exception.errorCode)
    }

    @Test
    fun `확인 대기로 공개를 거부해도 마감은 커밋되고, 제외하고 열면 확정 금액만 반영한다`() {
        val owner = userRepository.save(User(kakaoId = 1L, nickname = "지유", profileImageUrl = null))
        val funding = saveFunding(owner.id)
        val awaiting = saveContribution(funding.id, 10_000L)
        val pending = saveContribution(funding.id, 20_000L, ContributionStatus.PENDING_CONFIRMATION)
        saveContribution(funding.id, 30_000L, ContributionStatus.CONFIRMED)

        val exception = assertFailsWith<BusinessException> {
            service.openResult(owner.id, funding.id, excludePendingContributions = false)
        }
        assertEquals(FundingErrorCode.PENDING_CONTRIBUTION_EXISTS, exception.errorCode)
        val rejectedFunding = fundingRepository.findById(funding.id).orElseThrow()
        assertEquals(FundingStatus.CLOSED, rejectedFunding.status)
        assertEquals(null, rejectedFunding.resultOpenedAt)
        assertEquals(
            ContributionStatus.MESSAGE_ONLY,
            contributionRepository.findById(awaiting.id).orElseThrow().status,
        )

        val result = service.openResult(owner.id, funding.id, excludePendingContributions = true)

        assertEquals(30_000L, result.confirmedAmount)
        assertEquals(
            ContributionStatus.PENDING_CONFIRMATION,
            contributionRepository.findById(pending.id).orElseThrow().status,
        )
        assertEquals(FundingStatus.GIFT_PREPARING, fundingRepository.findById(funding.id).orElseThrow().status)
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

    private fun saveFunding(ownerId: Long): Funding =
        fundingRepository.saveAndFlush(
            Funding.createBirthday(
                ownerId = ownerId,
                targetAmount = 100_000L,
                deadline = NOW.minusDays(1),
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
                now = NOW.minusDays(5),
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
