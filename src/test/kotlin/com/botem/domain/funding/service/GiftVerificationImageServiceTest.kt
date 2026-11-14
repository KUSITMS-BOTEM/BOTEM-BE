package com.botem.domain.funding.service

import com.botem.domain.account.entity.Bank
import com.botem.domain.funding.entity.Funding
import com.botem.domain.funding.entity.FundingPayoutAccountSnapshot
import com.botem.domain.funding.entity.FundingProductSnapshot
import com.botem.domain.funding.entity.FundingType
import com.botem.domain.funding.entity.GiftVerification
import com.botem.domain.funding.entity.GiftVerificationStatus
import com.botem.domain.funding.exception.FundingErrorCode
import com.botem.domain.funding.repository.FundingRepository
import com.botem.domain.funding.repository.GiftVerificationRepository
import com.botem.global.exception.BusinessException
import com.botem.global.exception.CommonErrorCode
import com.botem.global.property.S3Properties
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import java.time.LocalDateTime
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GiftVerificationImageServiceTest {

    private val giftVerificationRepository = mock(GiftVerificationRepository::class.java)
    private val fundingRepository = mock(FundingRepository::class.java)
    private val s3Presigner = S3Presigner.builder()
        .region(Region.AP_NORTHEAST_2)
        .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
        .build()
    private val service = GiftVerificationImageService(
        giftVerificationRepository = giftVerificationRepository,
        fundingRepository = fundingRepository,
        s3Presigner = s3Presigner,
        s3Properties = S3Properties("botem-test", "ap-northeast-2"),
    )

    @AfterEach
    fun tearDown() {
        s3Presigner.close()
    }

    @Test
    fun `선물 수신자 경로에 크기가 서명된 업로드 URL을 발급한다`() {
        givenPendingVerification(funding())

        val response = service.createUploadUrl(USER_ID, VERIFICATION_ID, "image/png", MAX_CONTENT_LENGTH)

        assertTrue(
            response.imageUrl.startsWith(
                "https://botem-test.s3.ap-northeast-2.amazonaws.com/gift-verification/1/12/",
            ),
        )
        assertTrue(response.imageUrl.endsWith(".png"))
        assertTrue(response.uploadUrl.startsWith(response.imageUrl + "?"))
        assertTrue(response.uploadUrl.contains("content-length"))
        service.validateImageUrl(USER_ID, VERIFICATION_ID, response.imageUrl)
    }

    @Test
    fun `서프라이즈 펀딩 수신자도 업로드 URL을 발급받을 수 있다`() {
        givenPendingVerification(funding(type = FundingType.SURPRISE))

        val response = service.createUploadUrl(USER_ID, VERIFICATION_ID, "image/jpeg", 1L)

        assertTrue(response.imageUrl.endsWith(".jpg"))
    }

    @Test
    fun `지원하지 않는 이미지 형식과 파일 크기는 거부한다`() {
        givenPendingVerification(funding())

        listOf(
            Triple("image/gif", 1L, "contentType"),
            Triple("image/jpeg", 0L, "contentLength"),
            Triple("image/jpeg", MAX_CONTENT_LENGTH + 1, "contentLength"),
        ).forEach { (contentType, contentLength, detail) ->
            val exception = assertFailsWith<BusinessException> {
                service.createUploadUrl(USER_ID, VERIFICATION_ID, contentType, contentLength)
            }

            assertEquals(CommonErrorCode.INVALID_INPUT_VALUE, exception.errorCode, detail)
        }
    }

    @Test
    fun `존재하지 않는 인증이나 펀딩은 업로드 URL을 발급할 수 없다`() {
        given(giftVerificationRepository.findById(VERIFICATION_ID)).willReturn(Optional.empty())
        val verificationException = assertFailsWith<BusinessException> {
            service.createUploadUrl(USER_ID, VERIFICATION_ID, "image/jpeg", 1L)
        }
        assertEquals(FundingErrorCode.GIFT_VERIFICATION_NOT_FOUND, verificationException.errorCode)

        val verification = verification()
        given(giftVerificationRepository.findById(VERIFICATION_ID)).willReturn(Optional.of(verification))
        given(fundingRepository.findById(FUNDING_ID)).willReturn(Optional.empty())
        val fundingException = assertFailsWith<BusinessException> {
            service.createUploadUrl(USER_ID, VERIFICATION_ID, "image/jpeg", 1L)
        }
        assertEquals(FundingErrorCode.FUNDING_NOT_FOUND, fundingException.errorCode)
    }

    @Test
    fun `선물 수신자가 아니거나 완료된 인증에는 업로드 URL을 발급하지 않는다`() {
        givenPendingVerification(funding(ownerId = USER_ID + 1))
        val recipientException = assertFailsWith<BusinessException> {
            service.createUploadUrl(USER_ID, VERIFICATION_ID, "image/jpeg", 1L)
        }
        assertEquals(FundingErrorCode.GIFT_VERIFICATION_RECIPIENT_ONLY, recipientException.errorCode)

        val completed = verification().also { setField(it, "status", GiftVerificationStatus.COMPLETED) }
        given(giftVerificationRepository.findById(VERIFICATION_ID)).willReturn(Optional.of(completed))
        given(fundingRepository.findById(FUNDING_ID)).willReturn(Optional.of(funding()))
        val statusException = assertFailsWith<BusinessException> {
            service.createUploadUrl(USER_ID, VERIFICATION_ID, "image/jpeg", 1L)
        }
        assertEquals(FundingErrorCode.GIFT_VERIFICATION_NOT_COMPLETABLE, statusException.errorCode)
    }

    @Test
    fun `다른 사용자와 인증 경로 또는 변형된 이미지 URL은 거부한다`() {
        val validUrl = validImageUrl(USER_ID, VERIFICATION_ID)
        val invalidUrls = listOf(
            validImageUrl(USER_ID + 1, VERIFICATION_ID),
            validImageUrl(USER_ID, VERIFICATION_ID + 1),
            "https://evil.example.com/gift-verification/1/12/$IMAGE_FILE_NAME",
            "$validUrl?query=1",
        )

        invalidUrls.forEach { imageUrl ->
            val exception = assertFailsWith<BusinessException>(imageUrl) {
                service.validateImageUrl(USER_ID, VERIFICATION_ID, imageUrl)
            }
            assertEquals(CommonErrorCode.INVALID_INPUT_VALUE, exception.errorCode)
        }
    }

    private fun givenPendingVerification(funding: Funding) {
        given(giftVerificationRepository.findById(VERIFICATION_ID)).willReturn(Optional.of(verification()))
        given(fundingRepository.findById(FUNDING_ID)).willReturn(Optional.of(funding))
    }

    private fun verification(): GiftVerification =
        GiftVerification(FUNDING_ID).also { setField(it, "id", VERIFICATION_ID) }

    private fun funding(
        ownerId: Long = USER_ID,
        type: FundingType = FundingType.BIRTHDAY,
    ): Funding =
        Funding(
            ownerId = ownerId,
            recipientId = if (type == FundingType.SURPRISE) USER_ID else null,
            type = type,
            title = "선물 펀딩",
            targetAmount = 100_000L,
            deadline = LocalDateTime.of(2026, 11, 1, 0, 0),
            revealAt = if (type == FundingType.SURPRISE) LocalDateTime.of(2026, 11, 2, 0, 0) else null,
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
        ).also { setField(it, "id", FUNDING_ID) }

    private fun validImageUrl(userId: Long, verificationId: Long): String =
        "https://botem-test.s3.ap-northeast-2.amazonaws.com/gift-verification/$userId/$verificationId/$IMAGE_FILE_NAME"

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
        private const val MAX_CONTENT_LENGTH = 5L * 1024 * 1024
        private const val IMAGE_FILE_NAME = "3f2b8c1e-7a4d-4e9b-9c2a-1d5e6f7a8b9c.jpg"
    }
}
