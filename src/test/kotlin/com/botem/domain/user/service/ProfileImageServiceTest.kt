package com.botem.domain.user.service

import com.botem.global.exception.BusinessException
import com.botem.global.exception.CommonErrorCode
import com.botem.global.property.S3Properties
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.CALLS_REAL_METHODS
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response
import software.amazon.awssdk.services.s3.model.S3Exception
import software.amazon.awssdk.services.s3.model.S3Object
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ProfileImageServiceTest {

    private val s3Presigner = S3Presigner.builder()
        .region(Region.AP_NORTHEAST_2)
        .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
        .build()

    // 페이지네이터 등 SDK 기본 메서드는 실제로 동작시키고 네트워크 호출만 대체한다
    private val s3Client = mock(S3Client::class.java, CALLS_REAL_METHODS)
    private val profileImageService =
        ProfileImageService(s3Presigner, s3Client, S3Properties("botem-test", "ap-northeast-2"))

    @AfterEach
    fun tearDown() {
        s3Presigner.close()
    }

    @Test
    fun `본인 경로에 크기가 서명된 업로드 URL을 발급하고 발급한 이미지 URL은 검증을 통과한다`() {
        val response = profileImageService.createUploadUrl(USER_ID, "image/png", MAX_CONTENT_LENGTH)

        assertTrue(response.imageUrl.startsWith("https://botem-test.s3.ap-northeast-2.amazonaws.com/profile/1/"))
        assertTrue(response.imageUrl.endsWith(".png"))
        assertTrue(response.uploadUrl.startsWith(response.imageUrl + "?"))
        assertTrue(response.uploadUrl.contains("content-length"), "크기가 서명 헤더에 포함되어야 한다")
        profileImageService.validateImageUrl(USER_ID, response.imageUrl)
    }

    @Test
    fun `지원하지 않는 contentType이면 예외가 발생한다`() {
        val exception = assertFailsWith<BusinessException> {
            profileImageService.createUploadUrl(USER_ID, "image/gif", 1)
        }

        assertEquals(CommonErrorCode.INVALID_INPUT_VALUE, exception.errorCode)
    }

    @Test
    fun `파일 크기가 0 이하이거나 5MB를 넘으면 예외가 발생한다`() {
        listOf(0L, MAX_CONTENT_LENGTH + 1).forEach { contentLength ->
            val exception = assertFailsWith<BusinessException>("$contentLength") {
                profileImageService.createUploadUrl(USER_ID, "image/jpeg", contentLength)
            }

            assertEquals(CommonErrorCode.INVALID_INPUT_VALUE, exception.errorCode)
        }
    }

    @Test
    fun `외부 URL이나 다른 회원 경로의 이미지 URL은 허용하지 않는다`() {
        val otherUserImageUrl = profileImageService.createUploadUrl(2L, "image/jpeg", 1).imageUrl
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

    @Test
    fun `탈퇴하면 회원 경로의 이미지를 모두 삭제한다`() {
        val listResponse = ListObjectsV2Response.builder()
            .contents(S3Object.builder().key("profile/1/a.jpg").build(), S3Object.builder().key("profile/1/b.png").build())
            .isTruncated(false)
            .build()
        doReturn(listResponse).`when`(s3Client).listObjectsV2(any(ListObjectsV2Request::class.java))
        doReturn(DeleteObjectResponse.builder().build()).`when`(s3Client).deleteObject(any(DeleteObjectRequest::class.java))

        profileImageService.deleteAllImages(UserWithdrawnEvent(USER_ID))

        val listCaptor = ArgumentCaptor.forClass(ListObjectsV2Request::class.java)
        verify(s3Client).listObjectsV2(listCaptor.capture())
        assertEquals("profile/1/", listCaptor.value.prefix())

        val deleteCaptor = ArgumentCaptor.forClass(DeleteObjectRequest::class.java)
        verify(s3Client, times(2)).deleteObject(deleteCaptor.capture())
        assertEquals(listOf("profile/1/a.jpg", "profile/1/b.png"), deleteCaptor.allValues.map { it.key() })
    }

    @Test
    fun `이미지 삭제에 실패해도 예외를 전파하지 않는다`() {
        doThrow(S3Exception.builder().message("denied").build())
            .`when`(s3Client).listObjectsV2(any(ListObjectsV2Request::class.java))

        profileImageService.deleteAllImages(UserWithdrawnEvent(USER_ID))
    }

    companion object {
        private const val USER_ID = 1L
        private const val MAX_CONTENT_LENGTH = 5L * 1024 * 1024
    }
}
