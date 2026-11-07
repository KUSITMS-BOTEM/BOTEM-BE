package com.botem.domain.wish.exception

import com.botem.global.exception.ErrorCode
import org.springframework.http.HttpStatus

enum class WishErrorCode(
    override val status: HttpStatus,
    override val code: String,
    override val message: String,
) : ErrorCode {
    WISH_ALREADY_ADDED(HttpStatus.CONFLICT, "WISH_001", "이미 위시에 담은 상품입니다."),
}
