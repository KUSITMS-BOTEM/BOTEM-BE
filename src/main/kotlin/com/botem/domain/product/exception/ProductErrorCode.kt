package com.botem.domain.product.exception

import com.botem.global.exception.ErrorCode
import org.springframework.http.HttpStatus

enum class ProductErrorCode(
    override val status: HttpStatus,
    override val code: String,
    override val message: String,
) : ErrorCode {
    RECOMMENDED_PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "PRODUCT_001", "추천 상품을 찾을 수 없습니다."),
    RECOMMENDED_PRODUCT_DISCONTINUED(HttpStatus.CONFLICT, "PRODUCT_002", "판매가 종료된 상품입니다."),
    INVALID_PRODUCT_URL(HttpStatus.BAD_REQUEST, "PRODUCT_003", "https://로 시작하는 링크를 붙여넣어 주세요."),
    NOT_COUPANG_PRODUCT_URL(HttpStatus.BAD_REQUEST, "PRODUCT_004", "쿠팡 상품 링크만 보낼 수 있어요."),
}
