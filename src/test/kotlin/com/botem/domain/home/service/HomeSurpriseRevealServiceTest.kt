package com.botem.domain.home.service

import com.botem.domain.funding.repository.FundingRepository
import com.botem.domain.funding.repository.projection.RevealedSurpriseCardProjection
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HomeSurpriseRevealServiceTest {

    private val fundingRepository = mock(FundingRepository::class.java)
    private val clock = Clock.fixed(Instant.parse("2026-11-10T03:00:00Z"), ZoneId.of("Asia/Seoul"))
    private val service = HomeSurpriseRevealService(fundingRepository, clock)

    @Test
    fun `현재 시각까지 공개된 서프라이즈 카드를 응답으로 변환한다`() {
        val now = LocalDateTime.of(2026, 11, 10, 12, 0)
        given(fundingRepository.findRevealedSurpriseCards(USER_ID, now)).willReturn(
            listOf(
                RevealedSurpriseCardProjection(
                    fundingId = 24L,
                    productImageUrl = "https://image.example.com/surprise.jpg",
                    productName = "무선 헤드폰",
                    organizerName = "민지",
                    participantCount = 6L,
                    revealAt = LocalDateTime.of(2026, 11, 10, 0, 0),
                ),
            ),
        )

        val response = service.getCards(USER_ID)

        assertEquals(1, response.cards.size)
        assertEquals(24L, response.cards.single().fundingId)
        assertEquals("민지", response.cards.single().organizerName)
        assertEquals(6L, response.cards.single().participantCount)
        verify(fundingRepository).findRevealedSurpriseCards(USER_ID, now)
    }

    @Test
    fun `공개된 서프라이즈가 없으면 빈 카드 목록을 반환한다`() {
        val now = LocalDateTime.of(2026, 11, 10, 12, 0)
        given(fundingRepository.findRevealedSurpriseCards(USER_ID, now)).willReturn(emptyList())

        val response = service.getCards(USER_ID)

        assertTrue(response.cards.isEmpty())
    }

    companion object {
        private const val USER_ID = 1L
    }
}
