package com.botem.domain.funding.controller

import com.botem.domain.funding.dto.request.CompleteGiftVerificationRequest
import com.botem.domain.funding.dto.response.GiftVerificationImageUploadUrlResponse
import com.botem.domain.funding.dto.response.GiftVerificationResponse
import com.botem.domain.funding.entity.GiftVerificationStatus
import com.botem.domain.funding.service.GiftVerificationImageService
import com.botem.domain.funding.service.GiftVerificationService
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDateTime

@WebMvcTest(controllers = [GiftVerificationController::class])
class GiftVerificationControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var imageService: GiftVerificationImageService

    @MockitoBean
    private lateinit var giftVerificationService: GiftVerificationService

    @Test
    fun `선물 인증 이미지 업로드 URL을 발급한다`() {
        given(imageService.createUploadUrl(USER_ID, VERIFICATION_ID, "image/jpeg", 1024L))
            .willReturn(
                GiftVerificationImageUploadUrlResponse(
                    uploadUrl = "$IMAGE_URL?X-Amz-Algorithm=AWS4-HMAC-SHA256",
                    imageUrl = IMAGE_URL,
                ),
            )

        mockMvc.perform(
            post("/api/gift-verifications/{giftVerificationId}/image/upload-url", VERIFICATION_ID)
                .with(loggedIn())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{ "contentType": "image/jpeg", "contentLength": 1024 }"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.imageUrl").value(IMAGE_URL))

        verify(imageService).createUploadUrl(USER_ID, VERIFICATION_ID, "image/jpeg", 1024L)
    }

    @Test
    fun `선물 인증을 완료한다`() {
        val request = CompleteGiftVerificationRequest(
            imageUrl = IMAGE_URL,
            thankYouMessage = "고마워!",
            purchasedThroughBotemLink = true,
            replacementProductName = null,
        )
        given(giftVerificationService.complete(USER_ID, VERIFICATION_ID, request))
            .willReturn(
                GiftVerificationResponse(
                    giftVerificationId = VERIFICATION_ID,
                    fundingId = 7L,
                    status = GiftVerificationStatus.COMPLETED,
                    imageUrl = IMAGE_URL,
                    thankYouMessage = "고마워!",
                    purchasedThroughBotemLink = true,
                    replacementProductName = null,
                    completedAt = COMPLETED_AT,
                ),
            )

        mockMvc.perform(
            post("/api/gift-verifications/{giftVerificationId}/complete", VERIFICATION_ID)
                .with(loggedIn())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "imageUrl": "$IMAGE_URL",
                      "thankYouMessage": "고마워!",
                      "purchasedThroughBotemLink": true,
                      "replacementProductName": null
                    }
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.giftVerificationId").value(VERIFICATION_ID))
            .andExpect(jsonPath("$.data.status").value("COMPLETED"))
            .andExpect(jsonPath("$.data.imageUrl").value(IMAGE_URL))
            .andExpect(jsonPath("$.data.completedAt").value("2026-11-15T12:00:00"))

        verify(giftVerificationService).complete(USER_ID, VERIFICATION_ID, request)
    }

    @Test
    fun `잘못된 업로드 및 완료 요청은 400을 반환한다`() {
        mockMvc.perform(
            post("/api/gift-verifications/{giftVerificationId}/image/upload-url", VERIFICATION_ID)
                .with(loggedIn())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{ "contentType": "", "contentLength": 1024 }"""),
        ).andExpect(status().isBadRequest)

        mockMvc.perform(
            post("/api/gift-verifications/{giftVerificationId}/complete", VERIFICATION_ID)
                .with(loggedIn())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "imageUrl": "",
                      "thankYouMessage": null,
                      "purchasedThroughBotemLink": true,
                      "replacementProductName": "${"가".repeat(101)}"
                    }
                    """.trimIndent(),
                ),
        ).andExpect(status().isBadRequest)

        verifyNoInteractions(imageService, giftVerificationService)
    }

    @Test
    fun `인증 없이 선물 인증 API를 호출하면 401을 반환한다`() {
        mockMvc.perform(
            post("/api/gift-verifications/{giftVerificationId}/image/upload-url", VERIFICATION_ID)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{ "contentType": "image/jpeg", "contentLength": 1024 }"""),
        ).andExpect(status().isUnauthorized)

        verifyNoInteractions(imageService, giftVerificationService)
    }

    private fun loggedIn() =
        authentication(UsernamePasswordAuthenticationToken(USER_ID, null, emptyList()))

    companion object {
        private const val USER_ID = 1L
        private const val VERIFICATION_ID = 12L
        private const val IMAGE_URL =
            "https://botem-test.s3.ap-northeast-2.amazonaws.com/gift-verification/1/12/3f2b8c1e-7a4d-4e9b-9c2a-1d5e6f7a8b9c.jpg"
        private val COMPLETED_AT = LocalDateTime.of(2026, 11, 15, 12, 0)
    }
}
