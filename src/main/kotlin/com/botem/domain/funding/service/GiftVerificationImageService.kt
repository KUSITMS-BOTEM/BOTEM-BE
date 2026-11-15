package com.botem.domain.funding.service

import com.botem.domain.funding.dto.response.GiftVerificationImageUploadUrlResponse
import com.botem.domain.funding.entity.GiftVerificationStatus
import com.botem.domain.funding.exception.FundingErrorCode
import com.botem.domain.funding.repository.FundingRepository
import com.botem.domain.funding.repository.GiftVerificationRepository
import com.botem.global.exception.BusinessException
import com.botem.global.exception.CommonErrorCode
import com.botem.global.property.S3Properties
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import software.amazon.awssdk.core.exception.SdkException
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.S3Exception
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest
import java.time.Duration
import java.util.UUID

@Service
class GiftVerificationImageService(
    private val giftVerificationRepository: GiftVerificationRepository,
    private val fundingRepository: FundingRepository,
    private val s3Presigner: S3Presigner,
    private val s3Client: S3Client,
    private val s3Properties: S3Properties,
) {

    @Transactional(readOnly = true)
    fun createUploadUrl(
        userId: Long,
        giftVerificationId: Long,
        contentType: String,
        contentLength: Long,
    ): GiftVerificationImageUploadUrlResponse {
        validatePendingVerification(userId, giftVerificationId)
        val extension = EXTENSIONS_BY_CONTENT_TYPE[contentType]
            ?: throw BusinessException(
                CommonErrorCode.INVALID_INPUT_VALUE,
                "contentType: ${EXTENSIONS_BY_CONTENT_TYPE.keys.joinToString()} 중 하나여야 합니다",
            )
        if (contentLength !in 1..MAX_CONTENT_LENGTH) {
            throw BusinessException(CommonErrorCode.INVALID_INPUT_VALUE, "contentLength: 1바이트 이상 5MB 이하여야 합니다")
        }

        val key = "$KEY_PREFIX/$userId/$giftVerificationId/${UUID.randomUUID()}.$extension"
        val putObjectRequest = PutObjectRequest.builder()
            .bucket(s3Properties.bucket)
            .key(key)
            .contentType(contentType)
            .contentLength(contentLength)
            .build()
        val presignRequest = PutObjectPresignRequest.builder()
            .signatureDuration(UPLOAD_URL_EXPIRATION)
            .putObjectRequest(putObjectRequest)
            .build()

        return GiftVerificationImageUploadUrlResponse(
            uploadUrl = s3Presigner.presignPutObject(presignRequest).url().toString(),
            imageUrl = "${s3Properties.publicBaseUrl}/$key",
        )
    }

    fun validateImageUrl(
        userId: Long,
        giftVerificationId: Long,
        imageUrl: String,
    ) {
        val allowedUrl = Regex(
            Regex.escape("${s3Properties.publicBaseUrl}/$KEY_PREFIX/$userId/$giftVerificationId/") +
                "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(${EXTENSIONS_BY_CONTENT_TYPE.values.joinToString("|")})",
        )
        if (!allowedUrl.matches(imageUrl)) {
            throw BusinessException(
                CommonErrorCode.INVALID_INPUT_VALUE,
                "imageUrl: 업로드 URL 발급으로 받은 선물 인증 이미지 URL이어야 합니다",
            )
        }
    }

    fun validateUploadedImage(
        userId: Long,
        giftVerificationId: Long,
        imageUrl: String,
    ) {
        validateImageUrl(userId, giftVerificationId, imageUrl)
        val key = imageUrl.removePrefix("${s3Properties.publicBaseUrl}/")

        try {
            s3Client.headObject { request ->
                request.bucket(s3Properties.bucket).key(key)
            }
        } catch (exception: S3Exception) {
            if (exception.statusCode() == NOT_FOUND_STATUS) {
                throw BusinessException(FundingErrorCode.GIFT_VERIFICATION_IMAGE_NOT_UPLOADED)
            }
            throw BusinessException(FundingErrorCode.GIFT_VERIFICATION_IMAGE_CHECK_UNAVAILABLE)
        } catch (exception: SdkException) {
            throw BusinessException(FundingErrorCode.GIFT_VERIFICATION_IMAGE_CHECK_UNAVAILABLE)
        }
    }

    private fun validatePendingVerification(userId: Long, giftVerificationId: Long) {
        val verification = giftVerificationRepository.findById(giftVerificationId)
            .orElseThrow { BusinessException(FundingErrorCode.GIFT_VERIFICATION_NOT_FOUND) }
        val funding = fundingRepository.findById(verification.fundingId)
            .orElseThrow { BusinessException(FundingErrorCode.FUNDING_NOT_FOUND) }
        if (!funding.isGiftRecipient(userId)) {
            throw BusinessException(FundingErrorCode.GIFT_VERIFICATION_RECIPIENT_ONLY)
        }
        if (verification.status != GiftVerificationStatus.PENDING) {
            throw BusinessException(FundingErrorCode.GIFT_VERIFICATION_NOT_COMPLETABLE)
        }
    }

    companion object {
        private const val KEY_PREFIX = "gift-verification"
        private const val NOT_FOUND_STATUS = 404
        private const val MAX_CONTENT_LENGTH = 5L * 1024 * 1024
        private val UPLOAD_URL_EXPIRATION: Duration = Duration.ofMinutes(5)
        private val EXTENSIONS_BY_CONTENT_TYPE = mapOf(
            "image/jpeg" to "jpg",
            "image/png" to "png",
            "image/webp" to "webp",
        )
    }
}
