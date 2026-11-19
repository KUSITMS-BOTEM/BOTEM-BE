package com.botem.domain.funding.service

import com.botem.domain.account.entity.Bank
import com.botem.domain.funding.entity.Funding
import com.botem.domain.funding.entity.FundingPayoutAccountSnapshot
import com.botem.domain.funding.entity.FundingProductSnapshot
import com.botem.domain.funding.entity.FundingStatus
import com.botem.domain.funding.entity.FundingType
import com.botem.domain.funding.entity.RollingPaperAttachmentType
import com.botem.domain.funding.exception.FundingErrorCode
import com.botem.domain.funding.repository.FundingRepository
import com.botem.global.exception.BusinessException
import com.botem.global.exception.CommonErrorCode
import com.botem.global.property.S3Properties
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.BDDMockito.given
import org.mockito.Mockito.CALLS_REAL_METHODS
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.springframework.test.util.ReflectionTestUtils.setField
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.exception.SdkClientException
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.HeadObjectResponse
import software.amazon.awssdk.services.s3.model.S3Exception
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RollingPaperAttachmentServiceTest {

    private val fundingRepository = mock(FundingRepository::class.java)
    private val s3Presigner = S3Presigner.builder()
        .region(Region.AP_NORTHEAST_2)
        .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
        .build()
    private val s3Client = mock(S3Client::class.java, CALLS_REAL_METHODS)
    private val service = RollingPaperAttachmentService(
        fundingRepository = fundingRepository,
        s3Presigner = s3Presigner,
        s3Client = s3Client,
        s3Properties = S3Properties("botem-test", "ap-northeast-2"),
        clock = Clock.fixed(Instant.parse("2026-11-19T03:00:00Z"), ZoneId.of("Asia/Seoul")),
    )

    @AfterEach
    fun tearDown() {
        s3Presigner.close()
    }

    @Test
    fun `사진과 음성의 펀딩 전용 업로드 URL을 발급한다`() {
        givenFunding(funding())

        val photo = service.createUploadUrl(null, FUNDING_ID, RollingPaperAttachmentType.PHOTO, "image/png", 1024)
        val voice = service.createUploadUrl(USER_ID, FUNDING_ID, RollingPaperAttachmentType.VOICE, "audio/mp4", 2048)

        assertEquals(RollingPaperAttachmentType.PHOTO, photo.attachmentType)
        assertTrue(photo.fileUrl.matches(Regex("$PUBLIC_BASE_URL/rolling-paper/7/photo/.+\\.png")))
        assertTrue(photo.uploadUrl.startsWith(photo.fileUrl + "?"))
        assertTrue(photo.uploadUrl.contains("content-length"))
        assertTrue(voice.fileUrl.matches(Regex("$PUBLIC_BASE_URL/rolling-paper/7/voice/.+\\.m4a")))
    }

    @Test
    fun `첨부 종류별 지원 형식과 최대 크기를 검증한다`() {
        givenFunding(funding())

        listOf(
            UploadInput(RollingPaperAttachmentType.PHOTO, "image/gif", 1),
            UploadInput(RollingPaperAttachmentType.PHOTO, "image/jpeg", 0),
            UploadInput(RollingPaperAttachmentType.PHOTO, "image/jpeg", PHOTO_MAX_LENGTH + 1),
            UploadInput(RollingPaperAttachmentType.VOICE, "audio/wav", 1),
            UploadInput(RollingPaperAttachmentType.VOICE, "audio/webm", VOICE_MAX_LENGTH + 1),
        ).forEach { input ->
            val exception = assertFailsWith<BusinessException>(input.toString()) {
                service.createUploadUrl(null, FUNDING_ID, input.type, input.contentType, input.contentLength)
            }

            assertEquals(CommonErrorCode.INVALID_INPUT_VALUE, exception.errorCode)
        }
    }

    @Test
    fun `존재하지 않거나 메시지를 받을 수 없는 펀딩에는 업로드 URL을 발급하지 않는다`() {
        given(fundingRepository.findById(FUNDING_ID)).willReturn(Optional.empty())
        assertError(FundingErrorCode.FUNDING_NOT_FOUND) {
            service.createUploadUrl(null, FUNDING_ID, RollingPaperAttachmentType.PHOTO, "image/jpeg", 1)
        }

        listOf(
            funding(deadline = NOW),
            funding(status = FundingStatus.COMPLETED),
        ).forEach { funding ->
            givenFunding(funding)
            assertError(FundingErrorCode.FUNDING_CLOSED) {
                service.createUploadUrl(null, FUNDING_ID, RollingPaperAttachmentType.PHOTO, "image/jpeg", 1)
            }
        }
    }

    @Test
    fun `조기 마감한 일반 펀딩도 원래 마감일 전에는 첨부를 올릴 수 있다`() {
        givenFunding(funding(status = FundingStatus.CLOSED, earlyClosedAt = NOW.minusMinutes(1)))

        val response = service.createUploadUrl(null, FUNDING_ID, RollingPaperAttachmentType.PHOTO, "image/jpeg", 1)

        assertTrue(response.fileUrl.contains("/rolling-paper/$FUNDING_ID/photo/"))
    }

    @Test
    fun `공개 전 서프라이즈 수신자에게 업로드 경로를 노출하지 않는다`() {
        givenFunding(
            funding(
                type = FundingType.SURPRISE,
                recipientId = USER_ID,
                revealAt = NOW.plusDays(1),
            ),
        )

        assertError(FundingErrorCode.SURPRISE_NOT_REVEALED) {
            service.createUploadUrl(USER_ID, FUNDING_ID, RollingPaperAttachmentType.VOICE, "audio/webm", 1)
        }
    }

    @Test
    fun `업로드된 사진과 음성의 객체 메타데이터를 검증한다`() {
        doReturn(HeadObjectResponse.builder().contentType("image/jpeg").contentLength(1024).build())
            .`when`(s3Client).headObject(any(HeadObjectRequest::class.java))

        service.validateUploadedAttachment(FUNDING_ID, RollingPaperAttachmentType.PHOTO, validPhotoUrl())

        doReturn(HeadObjectResponse.builder().contentType("audio/webm").contentLength(2048).build())
            .`when`(s3Client).headObject(any(HeadObjectRequest::class.java))
        service.validateUploadedAttachment(FUNDING_ID, RollingPaperAttachmentType.VOICE, validVoiceUrl())
    }

    @Test
    fun `다른 펀딩과 첨부 종류 또는 변형된 URL은 거부한다`() {
        listOf(
            validPhotoUrl(fundingId = FUNDING_ID + 1),
            validVoiceUrl(),
            "https://evil.example.com/rolling-paper/7/photo/$PHOTO_FILE_NAME",
            "${validPhotoUrl()}?query=1",
        ).forEach { fileUrl ->
            val exception = assertFailsWith<BusinessException>(fileUrl) {
                service.validateUploadedAttachment(FUNDING_ID, RollingPaperAttachmentType.PHOTO, fileUrl)
            }

            assertEquals(CommonErrorCode.INVALID_INPUT_VALUE, exception.errorCode)
        }
    }

    @Test
    fun `업로드 객체의 형식이나 크기가 정책과 다르면 거부한다`() {
        listOf(
            HeadObjectResponse.builder().contentType("application/octet-stream").contentLength(1).build(),
            HeadObjectResponse.builder().contentType("image/jpeg").contentLength(0).build(),
            HeadObjectResponse.builder().contentType("image/jpeg").contentLength(PHOTO_MAX_LENGTH + 1).build(),
        ).forEach { metadata ->
            doReturn(metadata).`when`(s3Client).headObject(any(HeadObjectRequest::class.java))

            val exception = assertFailsWith<BusinessException> {
                service.validateUploadedAttachment(FUNDING_ID, RollingPaperAttachmentType.PHOTO, validPhotoUrl())
            }

            assertEquals(CommonErrorCode.INVALID_INPUT_VALUE, exception.errorCode)
        }
    }

    @Test
    fun `S3 객체가 없거나 확인할 수 없으면 첨부를 거부한다`() {
        listOf(
            S3Exception.builder().statusCode(404).message("not found").build() to
                FundingErrorCode.ROLLING_PAPER_ATTACHMENT_NOT_UPLOADED,
            S3Exception.builder().statusCode(500).message("unavailable").build() to
                FundingErrorCode.ROLLING_PAPER_ATTACHMENT_CHECK_UNAVAILABLE,
            SdkClientException.create("connection timeout") to
                FundingErrorCode.ROLLING_PAPER_ATTACHMENT_CHECK_UNAVAILABLE,
        ).forEach { (failure, expectedError) ->
            doThrow(failure).`when`(s3Client).headObject(any(HeadObjectRequest::class.java))

            assertError(expectedError) {
                service.validateUploadedAttachment(FUNDING_ID, RollingPaperAttachmentType.PHOTO, validPhotoUrl())
            }
        }
    }

    private fun givenFunding(funding: Funding) {
        given(fundingRepository.findById(FUNDING_ID)).willReturn(Optional.of(funding))
    }

    private fun funding(
        type: FundingType = FundingType.BIRTHDAY,
        recipientId: Long? = null,
        deadline: LocalDateTime = NOW.plusDays(1),
        revealAt: LocalDateTime? = null,
        status: FundingStatus = FundingStatus.FUNDING,
        earlyClosedAt: LocalDateTime? = null,
    ): Funding =
        Funding(
            ownerId = 2L,
            recipientId = recipientId,
            type = type,
            title = null,
            targetAmount = 100_000,
            deadline = deadline,
            revealAt = revealAt,
            story = "생일 축하해",
            promise = null,
            product = FundingProductSnapshot(null, "헤드폰", "https://image.example.com", 100_000, "https://shop.example.com"),
            payoutAccount = FundingPayoutAccountSnapshot(null, Bank.TOSS_BANK, "encrypted", "지유"),
        ).also {
            setField(it, "id", FUNDING_ID)
            setField(it, "status", status)
            setField(it, "earlyClosedAt", earlyClosedAt)
        }

    private fun validPhotoUrl(fundingId: Long = FUNDING_ID): String =
        "$PUBLIC_BASE_URL/rolling-paper/$fundingId/photo/$PHOTO_FILE_NAME"

    private fun validVoiceUrl(): String =
        "$PUBLIC_BASE_URL/rolling-paper/$FUNDING_ID/voice/$VOICE_FILE_NAME"

    private fun assertError(expected: FundingErrorCode, block: () -> Unit) {
        assertEquals(expected, assertFailsWith<BusinessException>(block = block).errorCode)
    }

    private data class UploadInput(
        val type: RollingPaperAttachmentType,
        val contentType: String,
        val contentLength: Long,
    )

    companion object {
        private const val USER_ID = 1L
        private const val FUNDING_ID = 7L
        private const val PUBLIC_BASE_URL = "https://botem-test.s3.ap-northeast-2.amazonaws.com"
        private const val PHOTO_FILE_NAME = "3f2b8c1e-7a4d-4e9b-9c2a-1d5e6f7a8b9c.jpg"
        private const val VOICE_FILE_NAME = "8a7b6c5d-4e3f-2a1b-9c8d-7e6f5a4b3c2d.webm"
        private const val PHOTO_MAX_LENGTH = 5L * 1024 * 1024
        private const val VOICE_MAX_LENGTH = 10L * 1024 * 1024
        private val NOW = LocalDateTime.of(2026, 11, 19, 12, 0)
    }
}
