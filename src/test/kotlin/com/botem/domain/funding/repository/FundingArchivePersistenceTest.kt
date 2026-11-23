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
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import java.time.LocalDateTime
import kotlin.test.assertEquals

@DataJpaTest
@Import(JpaAuditingConfig::class)
class FundingArchivePersistenceTest {

    @Autowired
    private lateinit var fundingRepository: FundingRepository

    @Autowired
    private lateinit var contributionRepository: ContributionRepository

    @Autowired
    private lateinit var rollingPaperMessageRepository: RollingPaperMessageRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    @Test
    fun `개설자와 수신자와 취소되지 않은 참여자의 마감 펀딩을 중복 없이 조회한다`() {
        val me = saveUser("나")
        val other = saveUser("친구")
        val opened = saveFunding(ownerId = me.id, deadline = NOW.minusDays(1))
        val received = saveFunding(
            ownerId = other.id,
            recipientId = me.id,
            type = FundingType.SURPRISE,
            deadline = NOW.minusDays(3),
            revealAt = NOW.minusHours(1),
            status = FundingStatus.WAITING_REVEAL,
        )
        val participated = saveFunding(ownerId = other.id, deadline = NOW.minusDays(2))
        saveContribution(participated.id, me.id, ContributionStatus.MESSAGE_ONLY)
        val cancelledParticipation = saveFunding(ownerId = other.id, deadline = NOW.minusDays(4))
        saveContribution(cancelledParticipation.id, me.id, ContributionStatus.AWAITING_TRANSFER, cancelled = true)
        saveFunding(ownerId = other.id, deadline = NOW.plusDays(1))

        val archived = fundingRepository.findArchivedFundingsByUserId(me.id, NOW)

        assertEquals(listOf(opened.id, participated.id, received.id), archived.map(Funding::id))
        assertEquals(setOf(participated.id), contributionRepository.findParticipatedFundingIds(archived.map(Funding::id), me.id).toSet())
    }

    @Test
    fun `펀딩별 최신 유효 롤링페이퍼와 전체 메시지 수를 함께 조회한다`() {
        val funding = saveFunding(ownerId = saveUser("개설자").id, deadline = NOW.minusDays(1))
        val writer = saveUser("하린", profileImageUrl = "https://image.example.com/harin.jpg")
        saveMessage(funding.id, writer.id, "첫 번째 메시지", "LIME_DOT_GRID")
        saveMessage(funding.id, writer.id, "음성 메시지", "BLUE_DOT_GRID", voiceUrl = "https://audio.example.com/voice.m4a")
        saveMessage(funding.id, writer.id, "취소된 메시지", "PINK_DOT_GRID", cancelled = true)

        val summary = rollingPaperMessageRepository.findArchiveSummariesByFundingIds(listOf(funding.id)).single()

        assertEquals(funding.id, summary.fundingId)
        assertEquals(2L, summary.messageCount)
        assertEquals("하린", summary.writerName)
        assertEquals("https://image.example.com/harin.jpg", summary.writerProfileImageUrl)
        assertEquals("BLUE_DOT_GRID", summary.paperTemplateCode)
        assertEquals("https://audio.example.com/voice.m4a", summary.voiceUrl)
    }

    private fun saveUser(name: String, profileImageUrl: String? = null): User =
        userRepository.saveAndFlush(
            User(
                kakaoId = nextKakaoId++,
                nickname = "$name 닉네임",
                profileImageUrl = profileImageUrl,
                name = name,
            ),
        )

    private fun saveFunding(
        ownerId: Long,
        deadline: LocalDateTime,
        recipientId: Long? = null,
        type: FundingType = FundingType.BIRTHDAY,
        revealAt: LocalDateTime? = null,
        status: FundingStatus = FundingStatus.FUNDING,
    ): Funding {
        val funding = Funding(
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
                    encryptedAccountNumber = "encrypted-account-number",
                    accountHolder = null,
                ),
            )
        setField(funding, "status", status)
        return fundingRepository.saveAndFlush(funding)
    }

    private fun saveContribution(
        fundingId: Long,
        participantUserId: Long,
        status: ContributionStatus,
        participantName: String = "참여자",
        cancelled: Boolean = false,
    ): Contribution =
        contributionRepository.saveAndFlush(
            Contribution(
                fundingId = fundingId,
                participantUserId = participantUserId,
                participantName = participantName,
                amount = if (status == ContributionStatus.MESSAGE_ONLY) 0 else 10_000L,
                status = status,
                guestPasswordHash = null,
            ).also { if (cancelled) it.cancel() },
        )

    private fun saveMessage(
        fundingId: Long,
        participantUserId: Long,
        content: String,
        paperTemplateCode: String,
        voiceUrl: String? = null,
        cancelled: Boolean = false,
    ) {
        val contribution = saveContribution(
            fundingId = fundingId,
            participantUserId = participantUserId,
            status = ContributionStatus.MESSAGE_ONLY,
            participantName = "하린",
            cancelled = cancelled,
        )
        rollingPaperMessageRepository.saveAndFlush(
            RollingPaperMessage(
                contributionId = contribution.id,
                content = content,
                paperTemplateCode = paperTemplateCode,
                voiceUrl = voiceUrl,
                voiceDurationSeconds = voiceUrl?.let { 10 },
            ),
        )
    }

    private fun setField(target: Any, fieldName: String, value: Any) {
        target.javaClass.getDeclaredField(fieldName).apply {
            isAccessible = true
            set(target, value)
        }
    }

    companion object {
        private val NOW = LocalDateTime.of(2026, 11, 21, 12, 0)
        private var nextKakaoId = 1L
    }
}
