package com.botem.domain.funding.controller

import com.botem.domain.funding.dto.response.RollingPaperAttachmentUploadUrlResponse
import com.botem.domain.funding.entity.RollingPaperAttachmentType
import com.botem.domain.funding.service.RollingPaperAttachmentService
import com.botem.domain.user.repository.UserRepository
import com.botem.global.config.SecurityConfig
import com.botem.global.jwt.JwtAuthenticationEntryPoint
import com.botem.global.jwt.JwtProvider
import com.botem.global.property.AdminProperties
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(controllers = [RollingPaperAttachmentController::class])
@Import(SecurityConfig::class)
@EnableConfigurationProperties(AdminProperties::class)
class RollingPaperAttachmentControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var attachmentService: RollingPaperAttachmentService

    @MockitoBean
    private lateinit var jwtProvider: JwtProvider

    @MockitoBean
    private lateinit var jwtAuthenticationEntryPoint: JwtAuthenticationEntryPoint

    @MockitoBean
    private lateinit var userRepository: UserRepository

    @Test
    fun `로그인 없이 롤링페이퍼 사진 업로드 URL을 발급한다`() {
        given(
            attachmentService.createUploadUrl(
                null,
                FUNDING_ID,
                RollingPaperAttachmentType.PHOTO,
                "image/jpeg",
                1024,
            ),
        ).willReturn(
            RollingPaperAttachmentUploadUrlResponse(
                attachmentType = RollingPaperAttachmentType.PHOTO,
                uploadUrl = "$FILE_URL?X-Amz-Algorithm=AWS4-HMAC-SHA256",
                fileUrl = FILE_URL,
            ),
        )

        mockMvc.perform(
            post("/api/fundings/{fundingId}/rolling-paper-attachments/upload-url", FUNDING_ID)
                .with(csrf())
                .contentType("application/json")
                .content(
                    """
                    {
                      "attachmentType": "PHOTO",
                      "contentType": "image/jpeg",
                      "contentLength": 1024
                    }
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.attachmentType").value("PHOTO"))
            .andExpect(jsonPath("$.data.fileUrl").value(FILE_URL))

        verify(attachmentService).createUploadUrl(
            null,
            FUNDING_ID,
            RollingPaperAttachmentType.PHOTO,
            "image/jpeg",
            1024,
        )
    }

    @Test
    fun `첨부 종류가 없거나 파일 정보 형식이 잘못되면 발급하지 않는다`() {
        listOf(
            """{ "contentType": "image/jpeg", "contentLength": 1024 }""",
            """{ "attachmentType": "FILE", "contentType": "image/jpeg", "contentLength": 1024 }""",
            """{ "attachmentType": "PHOTO", "contentType": "", "contentLength": 1024 }""",
            """{ "attachmentType": "PHOTO", "contentType": "image/jpeg", "contentLength": null }""",
        ).forEach { body ->
            mockMvc.perform(
                post("/api/fundings/{fundingId}/rolling-paper-attachments/upload-url", FUNDING_ID)
                    .with(csrf())
                    .contentType("application/json")
                    .content(body),
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error.code").value("COMMON_001"))
        }

        verifyNoInteractions(attachmentService)
    }

    companion object {
        private const val FUNDING_ID = 7L
        private const val FILE_URL =
            "https://botem-test.s3.ap-northeast-2.amazonaws.com/rolling-paper/7/photo/3f2b8c1e-7a4d-4e9b-9c2a-1d5e6f7a8b9c.jpg"
    }
}
