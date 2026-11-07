package com.botem.domain.product.controller

import com.botem.domain.product.service.RecommendedProductService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(controllers = [RecommendedProductController::class])
class RecommendedProductControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var recommendedProductService: RecommendedProductService

    @Test
    fun `없는 카테고리로 조회하면 400을 반환한다`() {
        mockMvc.perform(
            get("/api/recommended-products")
                .param("category", "UNKNOWN")
                .with(authentication(UsernamePasswordAuthenticationToken(1L, null, emptyList()))),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("COMMON_001"))
    }
}
