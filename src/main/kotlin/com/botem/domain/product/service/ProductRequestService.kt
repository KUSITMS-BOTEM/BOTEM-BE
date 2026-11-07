package com.botem.domain.product.service

import com.botem.domain.product.dto.request.RequestProductRequest
import com.botem.domain.product.entity.ProductRequest
import com.botem.domain.product.exception.RecommendedProductErrorCode
import com.botem.domain.product.repository.ProductRequestRepository
import com.botem.global.exception.BusinessException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.net.URI
import java.net.URISyntaxException

@Service
class ProductRequestService(
    private val productRequestRepository: ProductRequestRepository,
) {

    // 링크의 상품 정보는 불러오지 않고 형식과 쿠팡 도메인만 확인해 저장
    @Transactional
    fun requestProduct(userId: Long, request: RequestProductRequest) {
        val productUrl = request.productUrl.trim()
        validateCoupangUrl(productUrl)

        productRequestRepository.save(
            ProductRequest(
                userId = userId,
                productUrl = productUrl,
                productName = request.productName?.trim()?.takeIf(String::isNotEmpty),
            ),
        )
    }

    // 호스트를 정확히 비교해 "https://coupang.com@evil.com", "https://coupang.com.evil.com" 같은 우회를 막음
    private fun validateCoupangUrl(productUrl: String) {
        val uri = try {
            URI(productUrl)
        } catch (exception: URISyntaxException) {
            throw BusinessException(RecommendedProductErrorCode.INVALID_PRODUCT_URL)
        }

        if (!uri.scheme.equals(HTTPS_SCHEME, ignoreCase = true) || uri.host == null) {
            throw BusinessException(RecommendedProductErrorCode.INVALID_PRODUCT_URL)
        }
        if (uri.host.lowercase() !in COUPANG_HOSTS) {
            throw BusinessException(RecommendedProductErrorCode.NOT_COUPANG_PRODUCT_URL)
        }
    }

    companion object {
        private const val HTTPS_SCHEME = "https"
        private val COUPANG_HOSTS = setOf(
            "coupang.com",
            "www.coupang.com",
            "m.coupang.com",
            "link.coupang.com",
            "coupa.ng",
        )
    }
}
