package com.botem.domain.funding.entity

import com.botem.domain.funding.exception.FundingErrorCode
import com.botem.global.exception.BusinessException
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GiftVerificationTest {

    @Test
    fun `선물 인증을 완료하면 이미지와 감사 정보를 저장한다`() {
        val verification = GiftVerification(fundingId = 1L)

        verification.complete(
            imageUrl = IMAGE_URL,
            thankYouMessage = "고마워!",
            purchasedThroughBotemLink = true,
            completedAt = COMPLETED_AT,
            replacementProductName = "블루투스 스피커",
        )

        assertEquals(GiftVerificationStatus.COMPLETED, verification.status)
        assertEquals(IMAGE_URL, verification.imageUrl)
        assertEquals("고마워!", verification.thankYouMessage)
        assertEquals(true, verification.purchasedThroughBotemLink)
        assertEquals("블루투스 스피커", verification.replacementProductName)
        assertEquals(COMPLETED_AT, verification.completedAt)
    }

    @Test
    fun `보탬 링크 구매를 확인하지 않으면 선물 인증을 완료할 수 없다`() {
        val verification = GiftVerification(fundingId = 1L)

        val exception = assertFailsWith<BusinessException> {
            verification.complete(
                imageUrl = IMAGE_URL,
                thankYouMessage = null,
                purchasedThroughBotemLink = false,
                completedAt = COMPLETED_AT,
            )
        }

        assertEquals(FundingErrorCode.GIFT_PURCHASE_CONFIRMATION_REQUIRED, exception.errorCode)
    }

    @Test
    fun `완료된 선물 인증 재요청은 최초 완료 정보를 유지한다`() {
        val verification = GiftVerification(fundingId = 1L)
        verification.complete(IMAGE_URL, "첫 메시지", true, COMPLETED_AT)

        verification.complete(
            imageUrl = "https://image.example.com/changed.jpg",
            thankYouMessage = "변경 메시지",
            purchasedThroughBotemLink = true,
            completedAt = COMPLETED_AT.plusHours(1),
        )

        assertEquals(IMAGE_URL, verification.imageUrl)
        assertEquals("첫 메시지", verification.thankYouMessage)
        assertEquals(COMPLETED_AT, verification.completedAt)
    }

    @Test
    fun `완료 가능하지 않은 인증 상태에서는 완료할 수 없다`() {
        val verification = GiftVerification(fundingId = 1L)
        setField(verification, "status", GiftVerificationStatus.COMPLETED)

        val exception = assertFailsWith<BusinessException> {
            verification.complete(IMAGE_URL, null, true, COMPLETED_AT)
        }

        assertEquals(FundingErrorCode.GIFT_VERIFICATION_NOT_COMPLETABLE, exception.errorCode)
    }

    private fun setField(target: Any, fieldName: String, value: Any) {
        target.javaClass.getDeclaredField(fieldName).apply {
            isAccessible = true
            set(target, value)
        }
    }

    companion object {
        private const val IMAGE_URL = "https://image.example.com/gift.jpg"
        private val COMPLETED_AT = LocalDateTime.of(2026, 11, 15, 12, 0)
    }
}
