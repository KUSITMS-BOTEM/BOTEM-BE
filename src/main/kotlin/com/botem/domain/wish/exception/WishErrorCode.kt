package com.botem.domain.wish.exception

import com.botem.global.exception.ErrorCode
import org.springframework.http.HttpStatus

enum class WishErrorCode(
    override val status: HttpStatus,
    override val code: String,
    override val message: String,
) : ErrorCode {
    WISH_ALREADY_ADDED(HttpStatus.CONFLICT, "WISH_001", "이미 위시에 담은 상품입니다."),
    WISH_NOT_FOUND(HttpStatus.NOT_FOUND, "WISH_002", "위시를 찾을 수 없습니다."),
    WISH_IN_FUNDING(HttpStatus.CONFLICT, "WISH_003", "펀딩 중인 위시는 삭제할 수 없습니다."),
}
