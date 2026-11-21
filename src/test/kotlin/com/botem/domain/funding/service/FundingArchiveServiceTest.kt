package com.botem.domain.funding.service

import com.botem.domain.account.entity.Bank
import com.botem.domain.funding.dto.response.FundingArchiveRelationship
import com.botem.domain.funding.dto.response.FundingArchiveTab
import com.botem.domain.funding.entity.Funding
import com.botem.domain.funding.entity.FundingPayoutAccountSnapshot
import com.botem.domain.funding.entity.FundingProductSnapshot
import com.botem.domain.funding.entity.FundingType
import com.botem.domain.funding.repository.ContributionRepository
import com.botem.domain.funding.repository.FundingRepository
import com.botem.domain.funding.repository.RollingPaperMessageRepository
import com.botem.domain.funding.repository.projection.ArchiveRollingPaperSummaryProjection
import com.botem.domain.user.entity.User
import com.botem.domain.user.repository.UserRepository
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FundingArchiveServiceTest {

    private val fundingRepository = mock(FundingRepository::class.java)
    private val contributionRepository = mock(ContributionRepository::class.java)
    private val rollingPaperMessageRepository = mock(RollingPaperMessageRepository::class.java)
    private val userRepository = mock(UserRepository::class.java)
    private val service = FundingArchiveService(
        fundingRepository = fundingRepository,
        contributionRepository = contributionRepository,
        rollingPaperMessageRepository = rollingPaperMessageRepository,
        userRepository = userRepository,
        clock = Clock.fixed(Instant.parse("2026-11-21T03:00:00Z"), ZoneId.of("Asia/Seoul")),
    )

    @Test
    fun `전체 아카이브를 역할별 개수와 마감일 연도별 카드로 반환한다`() {
        val opened = funding(id = 1L, ownerId = ME_ID, deadline = LocalDateTime.of(2026, 10, 13, 23, 59))
        val received = funding(
            id = 2L,
            ownerId = OPENER_ID,
            recipientId = ME_ID,
            type = FundingType.SURPRISE,
            deadline = LocalDateTime.of(2026, 9, 13, 23, 59),
            revealAt = LocalDateTime.of(2026, 9, 14, 0, 0),
        )
        val participated = funding(id = 3L, ownerId = OPENER_ID, deadline = LocalDateTime.of(2025, 12, 31, 23, 59))
        given(fundingRepository.findArchivedFundingsByUserId(ME_ID, NOW)).willReturn(listOf(opened, received, participated))
        given(contributionRepository.findParticipatedFundingIds(listOf(1L, 2L, 3L), ME_ID)).willReturn(listOf(3L))
        val archiveSummary = summary(fundingId = 3L)
        given(rollingPaperMessageRepository.findArchiveSummariesByFundingIds(listOf(1L, 2L, 3L)))
            .willReturn(listOf(archiveSummary))
        given(userRepository.findAllById(listOf(ME_ID, OPENER_ID))).willReturn(
            listOf(user(ME_ID, "지유"), user(OPENER_ID, "서준", "https://image.example.com/seojun.jpg")),
        )

        val response = service.getArchive(ME_ID, FundingArchiveTab.ALL)

        assertEquals(3, response.tabCounts.all)
        assertEquals(1, response.tabCounts.received)
        assertEquals(1, response.tabCounts.participated)
        assertEquals(listOf(2026, 2025), response.years.map { it.year })
        assertEquals(listOf(1L, 2L), response.years[0].fundings.map { it.fundingId })
        assertEquals(FundingArchiveRelationship.OPENED, response.years[0].fundings[0].relationship)
        assertEquals(FundingArchiveRelationship.RECEIVED, response.years[0].fundings[1].relationship)
        assertEquals("서준", response.years[0].fundings[1].opener.name)
        assertEquals("지유", response.years[0].fundings[1].celebrantName)
        assertEquals(FundingArchiveRelationship.PARTICIPATED, response.years[1].fundings.single().relationship)
        assertEquals(2L, response.years[1].fundings.single().rollingPaperCount)
        assertEquals("축하해!", response.years[1].fundings.single().representativeMessage?.content)

        val receivedResponse = service.getArchive(ME_ID, FundingArchiveTab.RECEIVED)

        assertEquals(listOf(2L), receivedResponse.years.single().fundings.map { it.fundingId })
    }

    @Test
    fun `선택한 탭의 카드만 반환하고 메시지가 없으면 대표 메시지는 null이다`() {
        val opened = funding(id = 1L, ownerId = ME_ID, deadline = LocalDateTime.of(2026, 10, 13, 23, 59))
        val participated = funding(id = 2L, ownerId = OPENER_ID, deadline = LocalDateTime.of(2026, 9, 13, 23, 59))
        given(fundingRepository.findArchivedFundingsByUserId(ME_ID, NOW)).willReturn(listOf(opened, participated))
        given(contributionRepository.findParticipatedFundingIds(listOf(1L, 2L), ME_ID)).willReturn(listOf(2L))
        given(rollingPaperMessageRepository.findArchiveSummariesByFundingIds(listOf(1L, 2L))).willReturn(emptyList())
        given(userRepository.findAllById(listOf(ME_ID, OPENER_ID))).willReturn(listOf(user(ME_ID, "지유"), user(OPENER_ID, "서준")))

        val response = service.getArchive(ME_ID, FundingArchiveTab.PARTICIPATED)

        assertEquals(2, response.tabCounts.all)
        assertEquals(1, response.tabCounts.participated)
        assertEquals(listOf(2L), response.years.single().fundings.map { it.fundingId })
        assertEquals(0L, response.years.single().fundings.single().rollingPaperCount)
        assertNull(response.years.single().fundings.single().representativeMessage)
    }

    @Test
    fun `지난 펀딩이 없으면 추가 조회 없이 빈 아카이브를 반환한다`() {
        given(fundingRepository.findArchivedFundingsByUserId(ME_ID, NOW)).willReturn(emptyList())

        val response = service.getArchive(ME_ID, FundingArchiveTab.ALL)

        assertEquals(0, response.tabCounts.all)
        assertEquals(emptyList(), response.years)
        verifyNoInteractions(contributionRepository, rollingPaperMessageRepository, userRepository)
    }

    @Test
    fun `사용자 프로필이 없거나 이름이 없으면 비어 있는 이름 또는 닉네임으로 아카이브를 반환한다`() {
        val received = funding(
            id = 1L,
            ownerId = OPENER_ID,
            recipientId = ME_ID,
            type = FundingType.SURPRISE,
            deadline = LocalDateTime.of(2026, 10, 13, 23, 59),
            revealAt = LocalDateTime.of(2026, 10, 14, 0, 0),
        )
        given(fundingRepository.findArchivedFundingsByUserId(ME_ID, NOW)).willReturn(listOf(received))
        given(contributionRepository.findParticipatedFundingIds(listOf(1L), ME_ID)).willReturn(emptyList())
        given(rollingPaperMessageRepository.findArchiveSummariesByFundingIds(listOf(1L))).willReturn(emptyList())
        given(userRepository.findAllById(listOf(OPENER_ID, ME_ID))).willReturn(
            listOf(User(kakaoId = OPENER_ID, nickname = "서준 닉네임", profileImageUrl = null, name = null).also { setField(it, "id", OPENER_ID) }),
        )

        val card = service.getArchive(ME_ID, FundingArchiveTab.ALL).years.single().fundings.single()

        assertEquals("서준 닉네임", card.opener.name)
        assertEquals("", card.celebrantName)
    }

    private fun summary(fundingId: Long): ArchiveRollingPaperSummaryProjection =
        mock(ArchiveRollingPaperSummaryProjection::class.java).also {
            given(it.fundingId).willReturn(fundingId)
            given(it.messageCount).willReturn(2L)
            given(it.writerName).willReturn("하린")
            given(it.writerProfileImageUrl).willReturn(null)
            given(it.content).willReturn("축하해!")
            given(it.paperTemplateCode).willReturn("LIME_DOT_GRID")
            given(it.hasVoice).willReturn(false)
        }

    private fun funding(
        id: Long,
        ownerId: Long,
        deadline: LocalDateTime,
        recipientId: Long? = null,
        type: FundingType = FundingType.BIRTHDAY,
        revealAt: LocalDateTime? = null,
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
                name = "에어팟 프로 2",
                imageUrl = "https://image.example.com/product.jpg",
                price = 300_000L,
                url = "https://shop.example.com/product",
            ),
            payoutAccount = FundingPayoutAccountSnapshot(
                sourcePayoutAccountId = null,
                bank = Bank.KAKAO_BANK,
                encryptedAccountNumber = "encrypted",
                accountHolder = null,
            ),
        ).also { setField(it, "id", id) }

    private fun user(id: Long, name: String, profileImageUrl: String? = null): User =
        User(kakaoId = id, nickname = "$name 닉네임", profileImageUrl = profileImageUrl, name = name)
            .also { setField(it, "id", id) }

    private fun setField(target: Any, fieldName: String, value: Any) {
        target.javaClass.getDeclaredField(fieldName).apply {
            isAccessible = true
            set(target, value)
        }
    }

    companion object {
        private const val ME_ID = 1L
        private const val OPENER_ID = 2L
        private val NOW = LocalDateTime.of(2026, 11, 21, 12, 0)
    }
}
