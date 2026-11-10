package com.botem.global.config

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest(properties = ["admin.api-key=$ADMIN_KEY"])
@AutoConfigureMockMvc
class AdminApiKeySecurityTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `운영자 키 없이 운영자 API를 호출하면 401로 응답한다`() {
        mockMvc.perform(createRequest())
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code").value("AUTH_001"))
    }

    @Test
    fun `잘못된 운영자 키로 호출하면 401로 응답한다`() {
        mockMvc.perform(createRequest().header(HEADER, "wrong-key"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `로그인한 회원이라도 운영자 키가 없으면 401로 응답한다`() {
        mockMvc.perform(createRequest().with(authentication(UsernamePasswordAuthenticationToken(1L, null, emptyList()))))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `올바른 운영자 키로 추천 상품을 등록할 수 있다`() {
        mockMvc.perform(createRequest().header(HEADER, ADMIN_KEY))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.data.sellerName").value("쿠팡 Apple 공식 브랜드관"))
            .andExpect(jsonPath("$.data.status").value("ON_SALE"))
    }

    @Test
    fun `가격이 0 이하면 400으로 응답한다`() {
        mockMvc.perform(createRequest(price = 0).header(HEADER, ADMIN_KEY))
            .andExpect(status().isBadRequest)
    }

    private fun createRequest(price: Long = 429_000): MockHttpServletRequestBuilder =
        post("/api/admin/recommended-products")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {
                  "name": "소니 WH-1000XM5",
                  "imageUrl": "https://image.coupangcdn.com/example.jpg",
                  "price": $price,
                  "sellerName": "쿠팡 Apple 공식 브랜드관",
                  "productUrl": "https://link.coupang.com/a/example",
                  "category": "EARPHONE_HEADPHONE"
                }
                """,
            )

    companion object {
        private const val HEADER = "X-Admin-Key"
    }
}

private const val ADMIN_KEY = "test-admin-key"
