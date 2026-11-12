package com.botem.domain.friend.exception

import com.botem.global.exception.ErrorCode
import org.springframework.http.HttpStatus

enum class FriendErrorCode(
    override val status: HttpStatus,
    override val code: String,
    override val message: String,
) : ErrorCode {
    FRIEND_TARGET_NOT_FOUND(HttpStatus.NOT_FOUND, "FRIEND_001", "친구를 맺을 회원을 찾을 수 없습니다."),
    FRIEND_NOT_FOUND(HttpStatus.NOT_FOUND, "FRIEND_002", "친구가 아닌 회원입니다."),
}
