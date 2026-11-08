package com.botem.domain.home.exception

import com.botem.global.exception.ErrorCode
import org.springframework.http.HttpStatus

enum class HomeErrorCode(
    override val status: HttpStatus,
    override val code: String,
    override val message: String,
) : ErrorCode {
    FUNDING_CARD_EXISTS(HttpStatus.CONFLICT, "HOME_001", "홈에 표시할 펀딩이 존재합니다."),
}
