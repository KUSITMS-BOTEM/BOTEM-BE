package com.botem.domain.product.controller

import com.botem.domain.product.dto.response.RecommendedProductResponse
import com.botem.domain.product.entity.ProductCategory
import com.botem.domain.product.entity.RecommendedProductStatus
import com.botem.domain.product.service.RecommendedProductService
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
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
    fun `카테고리로 추천 상품 목록을 조회한다`() {
        given(recommendedProductService.getRecommendedProducts(ProductCategory.EARPHONE_HEADPHONE))
            .willReturn(listOf(productResponse()))

        mockMvc.perform(
            get("/api/recommended-products")
                .param("category", "EARPHONE_HEADPHONE")
                .with(authentication(UsernamePasswordAuthenticationToken(1L, null, emptyList()))),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data[0].id").value(PRODUCT_ID))
            .andExpect(jsonPath("$.data[0].category").value("EARPHONE_HEADPHONE"))
    }

    @Test
    fun `추천 상품 상세를 조회한다`() {
        given(recommendedProductService.getRecommendedProduct(PRODUCT_ID)).willReturn(productResponse())

        mockMvc.perform(
            get("/api/recommended-products/$PRODUCT_ID")
                .with(authentication(UsernamePasswordAuthenticationToken(1L, null, emptyList()))),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.name").value("에어팟 프로 3"))
            .andExpect(jsonPath("$.data.sellerName").value("Apple 공식 브랜드관"))
    }

    private fun productResponse(): RecommendedProductResponse =
        RecommendedProductResponse(
            id = PRODUCT_ID,
            name = "에어팟 프로 3",
            imageUrl = "https://example.com/airpods.jpg",
            price = 350_000,
            sellerName = "Apple 공식 브랜드관",
            category = ProductCategory.EARPHONE_HEADPHONE,
            status = RecommendedProductStatus.ON_SALE,
        )

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

    companion object {
        private const val PRODUCT_ID = 1L
    }
}
