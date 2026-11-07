package com.botem.domain.user.exception

import com.botem.global.exception.ErrorCode
import org.springframework.http.HttpStatus

enum class UserErrorCode(
    override val status: HttpStatus,
    override val code: String,
    override val message: String,
) : ErrorCode {
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_001", "사용자를 찾을 수 없습니다."),
    ACTIVE_FUNDING_EXISTS(HttpStatus.CONFLICT, "USER_002", "진행 중인 펀딩이 끝난 뒤 탈퇴할 수 있어요."),
    WITHDRAWAL_CHECK_UNAVAILABLE(
        HttpStatus.SERVICE_UNAVAILABLE,
        "USER_003",
        "현재 회원 탈퇴 가능 여부를 확인할 수 없습니다.",
    ),
    ONBOARDING_ALREADY_COMPLETED(HttpStatus.CONFLICT, "USER_004", "이미 온보딩을 완료한 사용자입니다."),
}
