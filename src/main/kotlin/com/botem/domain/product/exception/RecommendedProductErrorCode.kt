package com.botem.domain.product.exception

import com.botem.global.exception.ErrorCode
import org.springframework.http.HttpStatus

enum class RecommendedProductErrorCode(
    override val status: HttpStatus,
    override val code: String,
    override val message: String,
) : ErrorCode {
    RECOMMENDED_PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "PRODUCT_001", "추천 상품을 찾을 수 없습니다."),
    RECOMMENDED_PRODUCT_DISCONTINUED(HttpStatus.CONFLICT, "PRODUCT_002", "판매가 종료된 상품입니다."),
}
