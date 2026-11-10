package com.botem.domain.home.service

import com.botem.domain.funding.entity.ContributionStatus
import com.botem.domain.funding.entity.GiftVerificationStatus
import com.botem.domain.funding.repository.GiftVerificationRepository
import com.botem.domain.funding.repository.projection.PendingGiftVerificationTarget
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeGiftVerificationServiceTest {

    private val giftVerificationRepository = mock(GiftVerificationRepository::class.java)
    private val service = HomeGiftVerificationService(giftVerificationRepository)

    @Test
    fun `인증 대기 건수가 있으면 배너 노출 상태를 반환한다`() {
        given(
            giftVerificationRepository.countByRecipientAndStatus(
                USER_ID,
                GiftVerificationStatus.PENDING,
            ),
        ).willReturn(2L)

        val response = service.getBanner(USER_ID)

        assertTrue(response.visible)
        assertEquals(2L, response.pendingCount)
    }

    @Test
    fun `인증 대기 건수가 없으면 배너 숨김 상태를 반환한다`() {
        given(
            giftVerificationRepository.countByRecipientAndStatus(
                USER_ID,
                GiftVerificationStatus.PENDING,
            ),
        ).willReturn(0L)

        val response = service.getBanner(USER_ID)

        assertFalse(response.visible)
        assertEquals(0L, response.pendingCount)
    }

    @Test
    fun `취소된 참여를 제외해 인증 대상 목록을 조회하고 응답으로 변환한다`() {
        val target = PendingGiftVerificationTarget(
            giftVerificationId = 7L,
            fundingId = 12L,
            productName = "무선 헤드폰",
            productImageUrl = "https://image.example.com/product.jpg",
            fundingDeadline = LocalDateTime.of(2026, 11, 1, 23, 59),
            participantCount = 6L,
            replacementProductName = "스피커",
        )
        given(
            giftVerificationRepository.findTargetsByRecipientAndStatus(
                USER_ID,
                GiftVerificationStatus.PENDING,
                setOf(ContributionStatus.CANCELLED),
            ),
        ).willReturn(listOf(target))

        val response = service.getTargets(USER_ID)

        assertEquals(1, response.pendingCount)
        assertEquals(7L, response.targets.single().giftVerificationId)
        assertEquals(12L, response.targets.single().fundingId)
        assertEquals(6L, response.targets.single().participantCount)
        assertEquals("스피커", response.targets.single().replacementProductName)
        verify(giftVerificationRepository).findTargetsByRecipientAndStatus(
            USER_ID,
            GiftVerificationStatus.PENDING,
            setOf(ContributionStatus.CANCELLED),
        )
    }

    companion object {
        private const val USER_ID = 1L
    }
}
