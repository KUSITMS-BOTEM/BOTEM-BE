package com.botem.domain.funding.controller

import com.botem.domain.funding.dto.response.RollingPaperResponse
import com.botem.domain.funding.service.RollingPaperService
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(controllers = [RollingPaperController::class])
class RollingPaperControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var rollingPaperService: RollingPaperService

    @Test
    fun `로그인한 개설자가 롤링페이퍼를 조회한다`() {
        given(rollingPaperService.getRollingPaper(USER_ID, FUNDING_ID))
            .willReturn(RollingPaperResponse(FUNDING_ID, "지유", 0, emptyList()))

        mockMvc.perform(
            get("/api/fundings/{fundingId}/rolling-paper", FUNDING_ID)
                .with(authentication(UsernamePasswordAuthenticationToken(USER_ID, null, emptyList()))),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.celebrantName").value("지유"))
            .andExpect(jsonPath("$.data.messageCount").value(0))
    }

    @Test
    fun `로그인하지 않으면 롤링페이퍼를 조회할 수 없다`() {
        mockMvc.perform(get("/api/fundings/{fundingId}/rolling-paper", FUNDING_ID))
            .andExpect(status().isUnauthorized)

        verifyNoInteractions(rollingPaperService)
    }

    companion object {
        private const val USER_ID = 1L
        private const val FUNDING_ID = 12L
    }
}
