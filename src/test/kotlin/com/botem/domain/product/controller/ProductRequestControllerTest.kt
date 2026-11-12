package com.botem.domain.product.controller

import com.botem.domain.product.dto.request.RequestProductRequest
import com.botem.domain.product.service.ProductRequestService
import org.junit.jupiter.api.Test
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

@WebMvcTest(controllers = [ProductRequestController::class])
class ProductRequestControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var productRequestService: ProductRequestService

    @Test
    fun `상품 추가를 요청하면 201을 반환한다`() {
        mockMvc.perform(
            post("/api/product-requests")
                .with(authentication(UsernamePasswordAuthenticationToken(1L, null, emptyList())))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{ "productUrl": "https://www.coupang.com/vp/products/1", "productName": "텀블러" }"""),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.success").value(true))

        verify(productRequestService).requestProduct(
            1L,
            RequestProductRequest(productUrl = "https://www.coupang.com/vp/products/1", productName = "텀블러"),
        )
    }

    @Test
    fun `상품 링크가 비어 있으면 400을 반환한다`() {
        mockMvc.perform(
            post("/api/product-requests")
                .with(authentication(UsernamePasswordAuthenticationToken(1L, null, emptyList())))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{ "productUrl": "  " }"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("COMMON_001"))

        verifyNoInteractions(productRequestService)
    }
}
