package com.botem.domain.product.service

import com.botem.domain.product.dto.request.RequestProductRequest
import com.botem.domain.product.entity.ProductRequest
import com.botem.domain.product.exception.ProductErrorCode
import com.botem.domain.product.repository.ProductRequestRepository
import com.botem.global.exception.BusinessException
import com.botem.global.exception.CommonErrorCode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

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
                coupangProductId = COUPANG_PRODUCT_PATH_REGEX.find(productUrl)?.groupValues?.get(1),
                productName = normalizeProductName(request.productName),
            ),
        )
    }

    // 쿼리의 특수문자로 링크 전체 파싱이 실패하지 않도록 호스트만 읽어 정확히 비교
    // "https://www.coupang.com@evil.com"처럼 사용자 정보를 끼운 링크는 실제 호스트가 달라 거부
    private fun validateCoupangUrl(productUrl: String) {
        val authority = HTTPS_URL_REGEX.matchEntire(productUrl)?.groupValues?.get(1)
            ?: throw BusinessException(ProductErrorCode.INVALID_PRODUCT_URL)
        val host = authority.substringBefore(':').lowercase()

        if ('@' in authority || host !in COUPANG_HOSTS) {
            throw BusinessException(ProductErrorCode.NOT_COUPANG_PRODUCT_URL)
        }
    }

    private fun normalizeProductName(productName: String?): String? {
        val normalized = productName?.trim()?.takeIf(String::isNotEmpty)

        if (normalized != null && normalized.length > MAX_PRODUCT_NAME_LENGTH) {
            throw BusinessException(
                CommonErrorCode.INVALID_INPUT_VALUE,
                "productName: ${MAX_PRODUCT_NAME_LENGTH}자 이하여야 합니다",
            )
        }

        return normalized
    }

    companion object {
        private const val MAX_PRODUCT_NAME_LENGTH = 100
        private val HTTPS_URL_REGEX = Regex("https://([^/?#\\s]+)([/?#]\\S*)?", RegexOption.IGNORE_CASE)
        private val COUPANG_PRODUCT_PATH_REGEX = Regex("^https://[^/]+/v[pm]/products/(\\d{1,20})", RegexOption.IGNORE_CASE)
        private val COUPANG_HOSTS = setOf(
            "coupang.com",
            "www.coupang.com",
            "m.coupang.com",
            "link.coupang.com",
            "coupa.ng",
        )
    }
}
