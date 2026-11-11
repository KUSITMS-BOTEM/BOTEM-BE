package com.botem.domain.user.service

import com.botem.domain.user.dto.response.ProfileImageUploadUrlResponse
import com.botem.global.exception.BusinessException
import com.botem.global.exception.CommonErrorCode
import com.botem.global.property.S3Properties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.event.TransactionalEventListener
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest
import java.time.Duration
import java.util.UUID

@Service
class ProfileImageService(
    private val s3Presigner: S3Presigner,
    private val s3Client: S3Client,
    private val s3Properties: S3Properties,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    // 크기를 서명에 포함해 발급 시 검증한 크기와 다른 파일은 S3가 거부하도록 한다
    fun createUploadUrl(userId: Long, contentType: String, contentLength: Long): ProfileImageUploadUrlResponse {
        val extension = EXTENSIONS_BY_CONTENT_TYPE[contentType]
            ?: throw BusinessException(
                CommonErrorCode.INVALID_INPUT_VALUE,
                "contentType: ${EXTENSIONS_BY_CONTENT_TYPE.keys.joinToString()} 중 하나여야 합니다",
            )

        if (contentLength !in 1..MAX_CONTENT_LENGTH) {
            throw BusinessException(CommonErrorCode.INVALID_INPUT_VALUE, "contentLength: 1바이트 이상 5MB 이하여야 합니다")
        }

        val key = "$KEY_PREFIX/$userId/${UUID.randomUUID()}.$extension"

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

        return ProfileImageUploadUrlResponse(
            uploadUrl = s3Presigner.presignPutObject(presignRequest).url().toString(),
            imageUrl = "${s3Properties.publicBaseUrl}/$key",
        )
    }

    // 본인 경로로 발급된 형식의 URL만 허용해 외부 URL이나 다른 회원의 이미지가 저장되지 않도록 한다
    fun validateImageUrl(userId: Long, imageUrl: String) {
        val allowedUrl = Regex(
            Regex.escape("${s3Properties.publicBaseUrl}/$KEY_PREFIX/$userId/") +
                "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(${EXTENSIONS_BY_CONTENT_TYPE.values.joinToString("|")})",
        )

        if (!allowedUrl.matches(imageUrl)) {
            throw BusinessException(CommonErrorCode.INVALID_INPUT_VALUE, "profileImageUrl: 업로드 URL 발급으로 받은 이미지 URL이어야 합니다")
        }
    }

    // 탈퇴가 커밋된 뒤 실행해 롤백 시 이미지가 지워지지 않게 한다. 온보딩에 쓰이지 않은 업로드까지 경로 전체를 지운다
    // ponytail: 삭제 실패 시 재시도 없이 에러 로그(Sentry)만 남김. 실패가 잦으면 재시도 작업 추가
    @TransactionalEventListener
    fun deleteAllImages(event: UserWithdrawnEvent) {
        val prefix = "$KEY_PREFIX/${event.userId}/"

        runCatching {
            s3Client.listObjectsV2Paginator { it.bucket(s3Properties.bucket).prefix(prefix) }
                .contents()
                .forEach { image -> s3Client.deleteObject { it.bucket(s3Properties.bucket).key(image.key()) } }
        }.onFailure { log.error("탈퇴 회원 프로필 이미지 삭제 실패. prefix={}", prefix, it) }
    }

    companion object {
        private const val KEY_PREFIX = "profile"
        private const val MAX_CONTENT_LENGTH = 5L * 1024 * 1024
        private val UPLOAD_URL_EXPIRATION: Duration = Duration.ofMinutes(5)
        private val EXTENSIONS_BY_CONTENT_TYPE = mapOf(
            "image/jpeg" to "jpg",
            "image/png" to "png",
            "image/webp" to "webp",
        )
    }
}
