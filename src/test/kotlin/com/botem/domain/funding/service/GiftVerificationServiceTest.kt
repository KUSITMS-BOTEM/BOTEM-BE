package com.botem.domain.funding.service

import com.botem.domain.account.entity.Bank
import com.botem.domain.funding.dto.request.CompleteGiftVerificationRequest
import com.botem.domain.funding.entity.Funding
import com.botem.domain.funding.entity.FundingPayoutAccountSnapshot
import com.botem.domain.funding.entity.FundingProductSnapshot
import com.botem.domain.funding.entity.FundingStatus
import com.botem.domain.funding.entity.FundingType
import com.botem.domain.funding.entity.GiftVerification
import com.botem.domain.funding.entity.GiftVerificationStatus
import com.botem.domain.funding.exception.FundingErrorCode
import com.botem.domain.funding.repository.FundingRepository
import com.botem.domain.funding.repository.GiftVerificationRepository
import com.botem.global.exception.BusinessException
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GiftVerificationServiceTest {

    private val giftVerificationRepository = mock(GiftVerificationRepository::class.java)
    private val fundingRepository = mock(FundingRepository::class.java)
    private val imageService = mock(GiftVerificationImageService::class.java)
    private val service = GiftVerificationService(
        giftVerificationRepository = giftVerificationRepository,
        fundingRepository = fundingRepository,
        giftVerificationImageService = imageService,
        clock = Clock.fixed(Instant.parse("2026-11-15T03:00:00Z"), ZoneId.of("Asia/Seoul")),
    )

    @Test
    fun `선물 수신자가 인증과 펀딩을 함께 완료한다`() {
        val verification = verification()
        val funding = funding()
        givenLockedEntities(verification, funding)

        val response = service.complete(USER_ID, VERIFICATION_ID, request())

        verify(imageService).validateUploadedImage(USER_ID, VERIFICATION_ID, IMAGE_URL)
        assertEquals(GiftVerificationStatus.COMPLETED, verification.status)
        assertEquals(IMAGE_URL, response.imageUrl)
        assertEquals("고마워!", response.thankYouMessage)
        assertEquals("블루투스 스피커", response.replacementProductName)
        assertEquals(COMPLETED_AT, response.completedAt)
        assertEquals(FundingStatus.COMPLETED, funding.status)
        assertEquals(COMPLETED_AT, funding.completedAt)
    }

    @Test
    fun `감사 메시지와 대체 상품명의 공백을 정규화한다`() {
        val verification = verification()
        val funding = funding()
        givenLockedEntities(verification, funding)

        val response = service.complete(
            USER_ID,
            VERIFICATION_ID,
            request(thankYouMessage = "   ", replacementProductName = "   "),
        )

        assertEquals(null, response.thankYouMessage)
        assertEquals(null, response.replacementProductName)
    }

    @Test
    fun `선택 입력을 생략하면 감사 메시지와 대체 상품명을 저장하지 않는다`() {
        val verification = verification()
        val funding = funding()
        givenLockedEntities(verification, funding)

        val response = service.complete(
            USER_ID,
            VERIFICATION_ID,
            request(thankYouMessage = null, replacementProductName = null),
        )

        assertEquals(null, response.thankYouMessage)
        assertEquals(null, response.replacementProductName)
    }

    @Test
    fun `존재하지 않는 인증이나 펀딩은 완료할 수 없다`() {
        given(giftVerificationRepository.findByIdForUpdate(VERIFICATION_ID)).willReturn(null)
        val verificationException = assertFailsWith<BusinessException> {
            service.complete(USER_ID, VERIFICATION_ID, request())
        }
        assertEquals(FundingErrorCode.GIFT_VERIFICATION_NOT_FOUND, verificationException.errorCode)

        val verification = verification()
        given(giftVerificationRepository.findByIdForUpdate(VERIFICATION_ID)).willReturn(verification)
        given(fundingRepository.findByIdForUpdate(FUNDING_ID)).willReturn(null)
        val fundingException = assertFailsWith<BusinessException> {
            service.complete(USER_ID, VERIFICATION_ID, request())
        }
        assertEquals(FundingErrorCode.FUNDING_NOT_FOUND, fundingException.errorCode)
    }

    @Test
    fun `선물 수신자가 아니면 인증을 완료할 수 없다`() {
        givenLockedEntities(verification(), funding(ownerId = USER_ID + 1))

        val exception = assertFailsWith<BusinessException> {
            service.complete(USER_ID, VERIFICATION_ID, request())
        }

        assertEquals(FundingErrorCode.GIFT_VERIFICATION_RECIPIENT_ONLY, exception.errorCode)
        verify(imageService, never()).validateUploadedImage(USER_ID, VERIFICATION_ID, IMAGE_URL)
    }

    @Test
    fun `보탬 링크 구매 확인이 없으면 인증을 완료할 수 없다`() {
        val verification = verification()
        val funding = funding()
        givenLockedEntities(verification, funding)

        val exception = assertFailsWith<BusinessException> {
            service.complete(USER_ID, VERIFICATION_ID, request(purchasedThroughBotemLink = false))
        }

        assertEquals(FundingErrorCode.GIFT_PURCHASE_CONFIRMATION_REQUIRED, exception.errorCode)
        assertEquals(GiftVerificationStatus.PENDING, verification.status)
        assertEquals(FundingStatus.WAITING_GIFT_VERIFICATION, funding.status)
    }

    @Test
    fun `펀딩 상태가 인증 완료를 허용하지 않으면 완료할 수 없다`() {
        val verification = verification()
        val funding = funding(status = FundingStatus.GIFT_PREPARING)
        givenLockedEntities(verification, funding)

        val exception = assertFailsWith<BusinessException> {
            service.complete(USER_ID, VERIFICATION_ID, request())
        }

        assertEquals(FundingErrorCode.FUNDING_GIFT_VERIFICATION_NOT_COMPLETABLE, exception.errorCode)
        assertEquals(GiftVerificationStatus.PENDING, verification.status)
        verify(imageService, never()).validateUploadedImage(USER_ID, VERIFICATION_ID, IMAGE_URL)
    }

    @Test
    fun `완료된 인증 재요청은 이미지와 감사 정보를 변경하지 않는다`() {
        val verification = verification()
        val funding = funding()
        verification.complete(IMAGE_URL, "기존 메시지", true, COMPLETED_AT)
        funding.completeGiftVerification(COMPLETED_AT)
        givenLockedEntities(verification, funding)

        val response = service.complete(
            USER_ID,
            VERIFICATION_ID,
            request(
                imageUrl = "https://invalid.example.com/changed.jpg",
                thankYouMessage = "변경 메시지",
                purchasedThroughBotemLink = false,
            ),
        )

        verify(imageService, never()).validateUploadedImage(
            USER_ID,
            VERIFICATION_ID,
            "https://invalid.example.com/changed.jpg",
        )
        assertEquals(IMAGE_URL, response.imageUrl)
        assertEquals("기존 메시지", response.thankYouMessage)
        assertEquals(COMPLETED_AT, response.completedAt)
    }

    private fun givenLockedEntities(verification: GiftVerification, funding: Funding) {
        given(giftVerificationRepository.findByIdForUpdate(VERIFICATION_ID)).willReturn(verification)
        given(fundingRepository.findByIdForUpdate(FUNDING_ID)).willReturn(funding)
    }

    private fun verification(): GiftVerification =
        GiftVerification(FUNDING_ID).also { setField(it, "id", VERIFICATION_ID) }

    private fun funding(
        ownerId: Long = USER_ID,
        status: FundingStatus = FundingStatus.WAITING_GIFT_VERIFICATION,
    ): Funding =
        Funding(
            ownerId = ownerId,
            recipientId = null,
            type = FundingType.BIRTHDAY,
            title = "선물 펀딩",
            targetAmount = 100_000L,
            deadline = COMPLETED_AT.minusDays(1),
            revealAt = null,
            story = null,
            promise = null,
            product = FundingProductSnapshot(
                null,
                "무선 헤드폰",
                "https://image.example.com/product.jpg",
                100_000L,
                "https://shop.example.com/product",
            ),
            payoutAccount = FundingPayoutAccountSnapshot(null, Bank.KAKAO_BANK, "encrypted", "지유"),
        ).also { funding ->
            setField(funding, "id", FUNDING_ID)
            setField(funding, "status", status)
        }

    private fun request(
        imageUrl: String = IMAGE_URL,
        thankYouMessage: String? = "  고마워!  ",
        purchasedThroughBotemLink: Boolean = true,
        replacementProductName: String? = "  블루투스 스피커  ",
    ): CompleteGiftVerificationRequest =
        CompleteGiftVerificationRequest(
            imageUrl = imageUrl,
            thankYouMessage = thankYouMessage,
            purchasedThroughBotemLink = purchasedThroughBotemLink,
            replacementProductName = replacementProductName,
        )

    private fun setField(target: Any, fieldName: String, value: Any) {
        target.javaClass.getDeclaredField(fieldName).apply {
            isAccessible = true
            set(target, value)
        }
    }

    companion object {
        private const val USER_ID = 1L
        private const val FUNDING_ID = 7L
        private const val VERIFICATION_ID = 12L
        private const val IMAGE_URL =
            "https://botem-test.s3.ap-northeast-2.amazonaws.com/gift-verification/1/12/3f2b8c1e-7a4d-4e9b-9c2a-1d5e6f7a8b9c.jpg"
        private val COMPLETED_AT = LocalDateTime.of(2026, 11, 15, 12, 0)
    }
}
