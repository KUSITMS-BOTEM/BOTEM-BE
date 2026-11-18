package com.botem.domain.funding.repository

import com.botem.domain.account.entity.Bank
import com.botem.domain.funding.entity.Contribution
import com.botem.domain.funding.entity.ContributionStatus
import com.botem.domain.funding.entity.Funding
import com.botem.domain.funding.entity.FundingPayoutAccountSnapshot
import com.botem.domain.funding.entity.FundingProductSnapshot
import com.botem.domain.funding.entity.RollingPaperMessage
import com.botem.domain.user.entity.User
import com.botem.domain.user.repository.UserRepository
import com.botem.global.config.JpaAuditingConfig
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import java.time.LocalDateTime
import kotlin.test.assertEquals

@DataJpaTest
@Import(JpaAuditingConfig::class)
class RollingPaperMessageCountTest {

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var fundingRepository: FundingRepository

    @Autowired
    private lateinit var contributionRepository: ContributionRepository

    @Autowired
    private lateinit var rollingPaperMessageRepository: RollingPaperMessageRepository

    @Test
    fun `취소된 참여를 제외하고 해당 펀딩의 롤링페이퍼 메시지만 센다`() {
        val owner = userRepository.save(User(kakaoId = 1L, nickname = "지유", profileImageUrl = null))
        val funding = saveFunding(owner.id)
        val otherFunding = saveFunding(owner.id)

        saveMessage(funding.id, ContributionStatus.AWAITING_TRANSFER, 10_000L)
        saveMessage(funding.id, ContributionStatus.MESSAGE_ONLY, 0L)
        saveMessage(funding.id, ContributionStatus.AWAITING_TRANSFER, 5_000L, cancelled = true)
        saveMessage(otherFunding.id, ContributionStatus.MESSAGE_ONLY, 0L)

        assertEquals(2L, rollingPaperMessageRepository.countByFundingId(funding.id))
    }

    private fun saveMessage(fundingId: Long, status: ContributionStatus, amount: Long, cancelled: Boolean = false) {
        val contribution = contributionRepository.saveAndFlush(
            Contribution(
                fundingId = fundingId,
                participantUserId = null,
                participantName = "친구",
                amount = amount,
                status = status,
                guestPasswordHash = null,
            ).also { if (cancelled) it.cancel() },
        )
        rollingPaperMessageRepository.saveAndFlush(
            RollingPaperMessage(
                contributionId = contribution.id,
                content = "생일 축하해!",
                paperTemplateCode = "BLUE_DOT_GRID",
            ),
        )
    }

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

    companion object {
        private val NOW = LocalDateTime.of(2026, 11, 18, 12, 0)
    }
}
