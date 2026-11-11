package com.botem.domain.user.service

import com.botem.domain.user.dto.response.ProfileImageUploadUrlResponse
import com.botem.global.exception.BusinessException
import com.botem.global.exception.CommonErrorCode
import com.botem.global.property.S3Properties
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest
import java.time.Duration
import java.util.UUID

@Service
class ProfileImageService(
    private val s3Presigner: S3Presigner,
    private val s3Properties: S3Properties,
) {

    // ponytail: 업로드 용량은 제한하지 않음. 정책이 정해지면 contentLength를 서명에 포함해 제한
    fun createUploadUrl(userId: Long, contentType: String): ProfileImageUploadUrlResponse {
        val extension = EXTENSIONS_BY_CONTENT_TYPE[contentType]
            ?: throw BusinessException(
                CommonErrorCode.INVALID_INPUT_VALUE,
                "contentType: ${EXTENSIONS_BY_CONTENT_TYPE.keys.joinToString()} 중 하나여야 합니다",
            )
        val key = "$KEY_PREFIX/$userId/${UUID.randomUUID()}.$extension"

        val putObjectRequest = PutObjectRequest.builder()
            .bucket(s3Properties.bucket)
            .key(key)
            .contentType(contentType)
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

    companion object {
        private const val KEY_PREFIX = "profile"
        private val UPLOAD_URL_EXPIRATION: Duration = Duration.ofMinutes(5)
        private val EXTENSIONS_BY_CONTENT_TYPE = mapOf(
            "image/jpeg" to "jpg",
            "image/png" to "png",
            "image/webp" to "webp",
        )
    }
}
