package com.botem.domain.user.service

import com.botem.global.exception.BusinessException
import com.botem.global.exception.CommonErrorCode
import com.botem.global.property.S3Properties
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ProfileImageServiceTest {

    private val s3Presigner = S3Presigner.builder()
        .region(Region.AP_NORTHEAST_2)
        .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
        .build()
    private val profileImageService = ProfileImageService(s3Presigner, S3Properties("botem-test", "ap-northeast-2"))

    @AfterEach
    fun tearDown() {
        s3Presigner.close()
    }

    @Test
    fun `본인 경로에 업로드할 URL을 발급하고 발급한 이미지 URL은 검증을 통과한다`() {
        val response = profileImageService.createUploadUrl(USER_ID, "image/png")

        assertTrue(response.imageUrl.startsWith("https://botem-test.s3.ap-northeast-2.amazonaws.com/profile/1/"))
        assertTrue(response.imageUrl.endsWith(".png"))
        assertTrue(response.uploadUrl.startsWith(response.imageUrl + "?"))
        profileImageService.validateImageUrl(USER_ID, response.imageUrl)
    }

    @Test
    fun `지원하지 않는 contentType이면 예외가 발생한다`() {
        val exception = assertFailsWith<BusinessException> {
            profileImageService.createUploadUrl(USER_ID, "image/gif")
        }

        assertEquals(CommonErrorCode.INVALID_INPUT_VALUE, exception.errorCode)
    }

    @Test
    fun `외부 URL이나 다른 회원 경로의 이미지 URL은 허용하지 않는다`() {
        val otherUserImageUrl = profileImageService.createUploadUrl(2L, "image/jpeg").imageUrl
        val invalidUrls = listOf(
            otherUserImageUrl,
            "https://evil.example.com/profile/1/3f2b8c1e-7a4d-4e9b-9c2a-1d5e6f7a8b9c.jpg",
            "https://botem-test.s3.ap-northeast-2.amazonaws.com/profile/1/../2/3f2b8c1e-7a4d-4e9b-9c2a-1d5e6f7a8b9c.jpg",
            "https://botem-test.s3.ap-northeast-2.amazonaws.com/profile/1/3f2b8c1e-7a4d-4e9b-9c2a-1d5e6f7a8b9c.jpg?x=1",
        )

        invalidUrls.forEach { url ->
            assertFailsWith<BusinessException>(url) {
                profileImageService.validateImageUrl(USER_ID, url)
            }
        }
    }

    companion object {
        private const val USER_ID = 1L
    }
}
