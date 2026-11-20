package com.botem.global.exception

import org.springframework.http.HttpStatus

enum class CommonErrorCode(
    override val status: HttpStatus,
    override val code: String,
    override val message: String,
) : ErrorCode {
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "COMMON_001", "입력값이 올바르지 않습니다."),
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "COMMON_429", "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_500", "서버 내부 오류가 발생했습니다."),
}
