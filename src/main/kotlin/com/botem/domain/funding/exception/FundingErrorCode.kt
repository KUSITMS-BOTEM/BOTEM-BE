package com.botem.domain.funding.exception

import com.botem.global.exception.ErrorCode
import org.springframework.http.HttpStatus

enum class FundingErrorCode(
    override val status: HttpStatus,
    override val code: String,
    override val message: String,
) : ErrorCode {
    GIFT_PURCHASE_CONFIRMATION_REQUIRED(
        HttpStatus.BAD_REQUEST,
        "FUNDING_001",
        "선물 인증을 완료하려면 보탬 링크 구매 여부를 확인해야 합니다.",
    ),
}
