package com.botem.domain.product.service

import com.botem.domain.product.dto.request.RequestProductRequest
import com.botem.domain.product.exception.ProductErrorCode
import com.botem.domain.product.repository.ProductRequestRepository
import com.botem.global.config.JpaAuditingConfig
import com.botem.global.exception.BusinessException
import com.botem.global.exception.CommonErrorCode
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

@DataJpaTest
@Import(JpaAuditingConfig::class, ProductRequestService::class)
class ProductRequestServiceTest {

    @Autowired
    private lateinit var productRequestService: ProductRequestService

    @Autowired
    private lateinit var productRequestRepository: ProductRequestRepository

    @Test
    fun `앞뒤 공백을 지운 링크와 상품 이름을 저장하고 빈 상품 이름은 저장하지 않는다`() {
        productRequestService.requestProduct(
            USER_ID,
            RequestProductRequest(productUrl = "  https://www.coupang.com/vp/products/1  ", productName = " 다이슨 에어랩 "),
        )
        productRequestService.requestProduct(
            USER_ID,
            RequestProductRequest(productUrl = "https://link.coupang.com/a/abc", productName = "   "),
        )

        val requests = productRequestRepository.findAll().sortedBy { it.id }
        assertEquals("https://www.coupang.com/vp/products/1", requests[0].productUrl)
        assertEquals("다이슨 에어랩", requests[0].productName)
        assertNull(requests[1].productName)
    }

    @Test
    fun `같은 상품의 요청 수를 모을 수 있도록 링크에서 쿠팡 상품 ID를 꺼내 저장한다`() {
        listOf(
            "https://www.coupang.com/vp/products/7334985?itemId=1&vendorItemId=2&sourceType=search",
            "https://m.coupang.com/vm/products/7334985?q=에어팟",
            "https://link.coupang.com/a/abc",
        ).forEach { productRequestService.requestProduct(USER_ID, RequestProductRequest(productUrl = it)) }

        assertEquals(
            listOf("7334985", "7334985", null),
            productRequestRepository.findAll().sortedBy { it.id }.map { it.coupangProductId },
        )
    }

    @Test
    fun `상품 이름은 앞뒤 공백을 지운 뒤 길이를 확인한다`() {
        productRequestService.requestProduct(
            USER_ID,
            RequestProductRequest(productUrl = "https://coupa.ng/abc", productName = "  ${"가".repeat(100)}  "),
        )

        val exception = assertFailsWith<BusinessException> {
            productRequestService.requestProduct(
                USER_ID,
                RequestProductRequest(productUrl = "https://coupa.ng/abc", productName = "가".repeat(101)),
            )
        }

        assertEquals(CommonErrorCode.INVALID_INPUT_VALUE, exception.errorCode)
        assertEquals(1, productRequestRepository.count())
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "https://www.coupang.com/vp/products/1?a=b|c",
            "https://www.coupang.com:443/vp/products/1",
            "https://COUPANG.com/vp/products/1",
            "https://m.coupang.com/vm/products/1",
            "https://coupa.ng/abc",
        ],
    )
    fun `쿠팡 도메인 링크는 요청할 수 있다`(productUrl: String) {
        productRequestService.requestProduct(USER_ID, RequestProductRequest(productUrl = productUrl))

        assertEquals(1, productRequestRepository.count())
    }

    @ParameterizedTest
    @ValueSource(strings = ["http://www.coupang.com/vp/products/1", "www.coupang.com/vp/products/1", "https://", "https://쿠팡 링크"])
    fun `https 형식이 아닌 링크는 거부한다`(productUrl: String) {
        assertErrorCode(productUrl, ProductErrorCode.INVALID_PRODUCT_URL)
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "https://www.29cm.co.kr/products/1",
            "https://coupang.com.evil.com/vp/products/1",
            "https://www.coupang.com@evil.com/vp/products/1",
            "https://notcoupang.com/vp/products/1",
        ],
    )
    fun `쿠팡이 아닌 링크는 거부한다`(productUrl: String) {
        assertErrorCode(productUrl, ProductErrorCode.NOT_COUPANG_PRODUCT_URL)
    }

    private fun assertErrorCode(productUrl: String, errorCode: ProductErrorCode) {
        val exception = assertFailsWith<BusinessException> {
            productRequestService.requestProduct(USER_ID, RequestProductRequest(productUrl = productUrl))
        }

        assertEquals(errorCode, exception.errorCode)
        assertEquals(0, productRequestRepository.count())
    }

    companion object {
        private const val USER_ID = 1L
    }
}
