package com.botem.global.exception

class BusinessException(
    val errorCode: ErrorCode,
    val detail: String? = null,
) : RuntimeException(errorCode.message)
