package com.botem.domain.funding.service

import com.botem.domain.funding.dto.response.RollingPaperAttachmentUploadUrlResponse
import com.botem.domain.funding.entity.FundingStatus
import com.botem.domain.funding.entity.RollingPaperAttachmentType
import com.botem.domain.funding.exception.FundingErrorCode
import com.botem.domain.funding.repository.FundingRepository
import com.botem.global.exception.BusinessException
import com.botem.global.exception.CommonErrorCode
import com.botem.global.property.S3Properties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import software.amazon.awssdk.core.exception.SdkException
import software.amazon.awssdk.core.sync.ResponseTransformer
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.HeadObjectResponse
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.S3Exception
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest
import java.time.Clock
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID

@Service
class RollingPaperAttachmentService(
    private val fundingRepository: FundingRepository,
    private val s3Presigner: S3Presigner,
    private val s3Client: S3Client,
    private val s3Properties: S3Properties,
    private val clock: Clock,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional(readOnly = true)
    fun createUploadUrl(
        userId: Long?,
        fundingId: Long,
        attachmentType: RollingPaperAttachmentType,
        contentType: String,
        contentLength: Long,
    ): RollingPaperAttachmentUploadUrlResponse {
        validateFundingAcceptsMessages(userId, fundingId)
        val policy = policyOf(attachmentType)
        val extension = policy.extensionsByContentType[contentType]
            ?: throw invalidContentType(policy)
        validateContentLength(contentLength, policy)

        val key = "$KEY_PREFIX/$fundingId/${attachmentType.name.lowercase()}/${UUID.randomUUID()}.$extension"
        val putObjectRequest = PutObjectRequest.builder()
            .bucket(s3Properties.bucket)
            .key(key)
            .contentType(contentType)
            .contentLength(contentLength)
            .ifNoneMatch("*")
            .build()
        val presignRequest = PutObjectPresignRequest.builder()
            .signatureDuration(UPLOAD_URL_EXPIRATION)
            .putObjectRequest(putObjectRequest)
            .build()

        return RollingPaperAttachmentUploadUrlResponse(
            attachmentType = attachmentType,
            uploadUrl = s3Presigner.presignPutObject(presignRequest).url().toString(),
            fileUrl = "${s3Properties.publicBaseUrl}/$key",
        )
    }

    fun validateUploadedAttachment(
        fundingId: Long,
        attachmentType: RollingPaperAttachmentType,
        fileUrl: String,
    ) {
        val policy = policyOf(attachmentType)
        val key = extractKey(fundingId, attachmentType, fileUrl, policy)
        val objectMetadata = getObjectMetadata(key)

        if (objectMetadata.contentType() !in policy.extensionsByContentType.keys) {
            throw invalidContentType(policy)
        }
        validateContentLength(objectMetadata.contentLength(), policy)
        validateFileSignature(getObjectBytes(key), objectMetadata.contentType())
    }

    private fun validateFundingAcceptsMessages(userId: Long?, fundingId: Long) {
        val funding = fundingRepository.findById(fundingId)
            .orElseThrow { BusinessException(FundingErrorCode.FUNDING_NOT_FOUND) }
        val now = LocalDateTime.now(clock)

        if (funding.isHiddenFromRecipient(userId, now)) {
            throw BusinessException(FundingErrorCode.SURPRISE_NOT_REVEALED)
        }
        if (
            funding.status !in MESSAGE_ACCEPTABLE_STATUSES ||
            !funding.deadline.isAfter(now)
        ) {
            throw BusinessException(FundingErrorCode.FUNDING_CLOSED)
        }
    }

    private fun extractKey(
        fundingId: Long,
        attachmentType: RollingPaperAttachmentType,
        fileUrl: String,
        policy: AttachmentPolicy,
    ): String {
        val keyPrefix = "$KEY_PREFIX/$fundingId/${attachmentType.name.lowercase()}/"
        val allowedUrl = Regex(
            Regex.escape("${s3Properties.publicBaseUrl}/$keyPrefix") +
                UUID_PATTERN + "\\.(${policy.extensionsByContentType.values.joinToString("|")})",
        )
        if (!allowedUrl.matches(fileUrl)) {
            throw BusinessException(
                CommonErrorCode.INVALID_INPUT_VALUE,
                "fileUrl: 업로드 URL 발급으로 받은 ${policy.displayName} URL이어야 합니다",
            )
        }

        return fileUrl.removePrefix("${s3Properties.publicBaseUrl}/")
    }

    private fun getObjectMetadata(key: String): HeadObjectResponse =
        try {
            s3Client.headObject { request ->
                request.bucket(s3Properties.bucket).key(key)
            }
        } catch (exception: S3Exception) {
            if (exception.statusCode() == NOT_FOUND_STATUS) {
                throw BusinessException(FundingErrorCode.ROLLING_PAPER_ATTACHMENT_NOT_UPLOADED)
            }
            log.warn("S3 롤링페이퍼 첨부 확인 실패. key={}, status={}", key, exception.statusCode(), exception)
            throw BusinessException(FundingErrorCode.ROLLING_PAPER_ATTACHMENT_CHECK_UNAVAILABLE)
        } catch (exception: SdkException) {
            log.warn("S3 롤링페이퍼 첨부 확인 실패. key={}", key, exception)
            throw BusinessException(FundingErrorCode.ROLLING_PAPER_ATTACHMENT_CHECK_UNAVAILABLE)
        }

    private fun getObjectBytes(key: String): ByteArray =
        try {
            s3Client.getObject(
                GetObjectRequest.builder()
                    .bucket(s3Properties.bucket)
                    .key(key)
                    .range("bytes=0-$FILE_SIGNATURE_MAX_LENGTH")
                    .build(),
                ResponseTransformer.toBytes(),
            ).asByteArray()
        } catch (exception: S3Exception) {
            if (exception.statusCode() == NOT_FOUND_STATUS) {
                throw BusinessException(FundingErrorCode.ROLLING_PAPER_ATTACHMENT_NOT_UPLOADED)
            }
            log.warn("S3 롤링페이퍼 첨부 확인 실패. key={}, status={}", key, exception.statusCode(), exception)
            throw BusinessException(FundingErrorCode.ROLLING_PAPER_ATTACHMENT_CHECK_UNAVAILABLE)
        } catch (exception: SdkException) {
            log.warn("S3 롤링페이퍼 첨부 확인 실패. key={}", key, exception)
            throw BusinessException(FundingErrorCode.ROLLING_PAPER_ATTACHMENT_CHECK_UNAVAILABLE)
        }

    private fun validateFileSignature(content: ByteArray, contentType: String) {
        val valid = when (contentType) {
            "image/jpeg" -> content.startsWith(0xff, 0xd8, 0xff)
            "image/png" -> content.startsWith(0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
            "image/webp" -> content.startsWithAscii("RIFF") && content.startsWithAscii("WEBP", offset = 8)
            "audio/mp4" -> content.startsWithAscii("ftyp", offset = 4)
            "audio/mpeg" -> content.startsWithAscii("ID3") ||
                (content.size >= 2 && content[0].toInt() and 0xff == 0xff && content[1].toInt() and 0xe0 == 0xe0)
            "audio/webm" -> content.startsWith(0x1a, 0x45, 0xdf, 0xa3)
            "audio/ogg" -> content.startsWithAscii("OggS")
            else -> false
        }

        if (!valid) {
            throw BusinessException(CommonErrorCode.INVALID_INPUT_VALUE, "업로드한 파일의 실제 형식이 Content-Type과 일치하지 않습니다")
        }
    }

    private fun ByteArray.startsWith(vararg expected: Int): Boolean =
        size >= expected.size && expected.indices.all { index -> this[index].toInt() and 0xff == expected[index] }

    private fun ByteArray.startsWithAscii(expected: String, offset: Int = 0): Boolean {
        val expectedBytes = expected.encodeToByteArray()
        return size >= offset + expectedBytes.size && expectedBytes.indices.all { index ->
            this[offset + index] == expectedBytes[index]
        }
    }

    private fun validateContentLength(contentLength: Long, policy: AttachmentPolicy) {
        if (contentLength !in 1..policy.maxContentLength) {
            throw BusinessException(
                CommonErrorCode.INVALID_INPUT_VALUE,
                "contentLength: 1바이트 이상 ${policy.maxContentLengthInMb}MB 이하여야 합니다",
            )
        }
    }

    private fun invalidContentType(policy: AttachmentPolicy) =
        BusinessException(
            CommonErrorCode.INVALID_INPUT_VALUE,
            "contentType: ${policy.extensionsByContentType.keys.joinToString()} 중 하나여야 합니다",
        )

    private fun policyOf(attachmentType: RollingPaperAttachmentType): AttachmentPolicy =
        when (attachmentType) {
            RollingPaperAttachmentType.PHOTO -> PHOTO_POLICY
            RollingPaperAttachmentType.VOICE -> VOICE_POLICY
        }

    private data class AttachmentPolicy(
        val displayName: String,
        val maxContentLengthInMb: Long,
        val extensionsByContentType: Map<String, String>,
    ) {
        val maxContentLength: Long = maxContentLengthInMb * 1024 * 1024
    }

    companion object {
        private const val KEY_PREFIX = "rolling-paper"
        private const val NOT_FOUND_STATUS = 404
        private const val FILE_SIGNATURE_MAX_LENGTH = 31
        private const val UUID_PATTERN =
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
        private val MESSAGE_ACCEPTABLE_STATUSES = setOf(FundingStatus.FUNDING, FundingStatus.CLOSED)
        private val UPLOAD_URL_EXPIRATION: Duration = Duration.ofMinutes(5)
        private val PHOTO_POLICY = AttachmentPolicy(
            displayName = "사진",
            maxContentLengthInMb = 5,
            extensionsByContentType = mapOf(
                "image/jpeg" to "jpg",
                "image/png" to "png",
                "image/webp" to "webp",
            ),
        )
        private val VOICE_POLICY = AttachmentPolicy(
            displayName = "음성",
            maxContentLengthInMb = 10,
            extensionsByContentType = mapOf(
                "audio/mp4" to "m4a",
                "audio/mpeg" to "mp3",
                "audio/webm" to "webm",
                "audio/ogg" to "ogg",
            ),
        )
    }
}
