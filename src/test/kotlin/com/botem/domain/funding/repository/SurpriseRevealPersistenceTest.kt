package com.botem.domain.funding.repository

import com.botem.domain.account.entity.Bank
import com.botem.domain.funding.entity.Contribution
import com.botem.domain.funding.entity.ContributionStatus
import com.botem.domain.funding.entity.Funding
import com.botem.domain.funding.entity.FundingPayoutAccountSnapshot
import com.botem.domain.funding.entity.FundingProductSnapshot
import com.botem.domain.funding.entity.FundingStatus
import com.botem.domain.funding.entity.FundingType
import com.botem.domain.funding.entity.RollingPaperMessage
import com.botem.domain.user.entity.User
import com.botem.domain.user.repository.UserRepository
import com.botem.global.config.JpaAuditingConfig
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNull

@DataJpaTest
@Import(JpaAuditingConfig::class)
class SurpriseRevealPersistenceTest {

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var fundingRepository: FundingRepository

    @Autowired
    private lateinit var contributionRepository: ContributionRepository

    @Autowired
    private lateinit var rollingPaperMessageRepository: RollingPaperMessageRepository

    @Autowired
    private lateinit var entityManager: EntityManager

    @Test
    fun `취소되지 않은 로그인 및 비로그인 참여자의 롤링페이퍼를 참여 순으로 조회한다`() {
        val owner = saveUser("주최자")
        val recipient = saveUser("지유")
        val participant = saveUser("하은", "https://image.example.com/profile.jpg")
        val funding = saveFunding(owner.id, recipient.id)
        val memberContribution = contributionRepository.saveAndFlush(
            contribution(funding.id, participant.id, "하은"),
        )
        val guestContribution = contributionRepository.saveAndFlush(
            contribution(funding.id, null, "익명의 친구"),
        )
        val cancelledContribution = contributionRepository.saveAndFlush(
            contribution(funding.id, null, "취소한 친구").also(Contribution::cancel),
        )

        rollingPaperMessageRepository.saveAndFlush(
            RollingPaperMessage(
                contributionId = memberContribution.id,
                content = "생일 축하해!",
                paperTemplateCode = "BLUE_DOT_GRID",
            ),
        )
        rollingPaperMessageRepository.saveAndFlush(
            RollingPaperMessage(
                contributionId = guestContribution.id,
                content = "항상 고마워!",
                paperTemplateCode = "BLACK_CONFETTI",
                voiceUrl = "https://cdn.example.com/voice.m4a",
                voiceDurationSeconds = 12,
            ),
        )
        rollingPaperMessageRepository.saveAndFlush(
            RollingPaperMessage(
                contributionId = cancelledContribution.id,
                content = "조회되면 안 돼",
                paperTemplateCode = "LIME_DOT_GRID",
            ),
        )
        entityManager.clear()

        val messages = rollingPaperMessageRepository.findAllByFundingId(funding.id)

        assertEquals(listOf("하은", "익명의 친구"), messages.map { it.participantName })
        assertEquals("https://image.example.com/profile.jpg", messages.first().participantProfileImageUrl)
        assertNull(messages.last().participantProfileImageUrl)
        assertEquals(12, messages.last().voiceDurationSeconds)
    }

    private fun saveUser(name: String, profileImageUrl: String? = null): User =
        userRepository.saveAndFlush(
            User(
                kakaoId = nextKakaoId++,
                nickname = name,
                profileImageUrl = profileImageUrl,
                name = name,
            ),
        )

    private fun saveFunding(ownerId: Long, recipientId: Long): Funding =
        fundingRepository.saveAndFlush(
            Funding(
                ownerId = ownerId,
                recipientId = recipientId,
                type = FundingType.SURPRISE,
                title = "몰래 준비한 선물",
                targetAmount = 500_000L,
                deadline = NOW.minusDays(1),
                revealAt = NOW.minusMinutes(1),
                story = null,
                promise = null,
                product = FundingProductSnapshot(
                    sourceWishId = null,
                    name = "무선 헤드폰",
                    imageUrl = "https://image.example.com/product.jpg",
                    price = 500_000L,
                    url = "https://shop.example.com/product",
                ),
                payoutAccount = FundingPayoutAccountSnapshot(
                    sourcePayoutAccountId = null,
                    bank = Bank.KAKAO_BANK,
                    encryptedAccountNumber = "encrypted",
                    accountHolder = "민지",
                ),
            ).also { setField(it, "status", FundingStatus.WAITING_REVEAL) },
        )

    private fun contribution(
        fundingId: Long,
        participantUserId: Long?,
        participantName: String,
    ): Contribution =
        Contribution(
            fundingId = fundingId,
            participantUserId = participantUserId,
            participantName = participantName,
            amount = 0L,
            status = ContributionStatus.MESSAGE_ONLY,
            guestPasswordHash = if (participantUserId == null) "hashed-password" else null,
        )

    private fun setField(target: Any, fieldName: String, value: Any) {
        target.javaClass.getDeclaredField(fieldName).apply {
            isAccessible = true
            set(target, value)
        }
    }

    companion object {
        private val NOW = LocalDateTime.of(2026, 11, 15, 9, 41)
        private var nextKakaoId = 10_000L
    }
}
