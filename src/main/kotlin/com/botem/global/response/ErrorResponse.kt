package com.botem.global.response

data class ErrorResponse(
    val code: String,
    val message: String,
    val detail: String?,
)
