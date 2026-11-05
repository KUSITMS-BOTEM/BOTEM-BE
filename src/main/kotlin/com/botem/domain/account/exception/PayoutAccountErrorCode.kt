package com.botem.domain.account.exception

import com.botem.global.exception.ErrorCode
import org.springframework.http.HttpStatus

enum class PayoutAccountErrorCode(
    override val status: HttpStatus,
    override val code: String,
    override val message: String,
) : ErrorCode {
    PAYOUT_ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "PAYOUT_ACCOUNT_001", "받을 계좌를 찾을 수 없습니다."),
    MAX_ACCOUNT_COUNT_EXCEEDED(HttpStatus.BAD_REQUEST, "PAYOUT_ACCOUNT_002", "받을 계좌는 최대 2개까지 등록할 수 있습니다."),
    BANK_ALREADY_REGISTERED(HttpStatus.CONFLICT, "PAYOUT_ACCOUNT_003", "이미 등록한 은행입니다."),
    INVALID_ACCOUNT_NUMBER(HttpStatus.BAD_REQUEST, "PAYOUT_ACCOUNT_004", "계좌번호 형식이 올바르지 않습니다."),
}
