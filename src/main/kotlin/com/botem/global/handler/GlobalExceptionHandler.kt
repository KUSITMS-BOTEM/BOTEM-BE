package com.botem.global.handler

import com.botem.global.exception.BusinessException
import com.botem.global.exception.CommonErrorCode
import com.botem.global.response.ApiResponse
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(BusinessException::class)
    fun handleBusinessException(exception: BusinessException): ResponseEntity<ApiResponse<Nothing>> {
        val errorCode = exception.errorCode

        return ResponseEntity
            .status(errorCode.status)
            .body(ApiResponse.fail(errorCode, exception.detail))
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(
        exception: MethodArgumentNotValidException,
    ): ResponseEntity<ApiResponse<Nothing>> {
        val detail = exception.bindingResult.allErrors
            .filterIsInstance<FieldError>()
            .joinToString(", ") { "${it.field}: ${it.defaultMessage}" }

        return ResponseEntity
            .badRequest()
            .body(ApiResponse.fail(CommonErrorCode.INVALID_INPUT_VALUE, detail))
    }

    @ExceptionHandler(Exception::class)
    fun handleException(exception: Exception): ResponseEntity<ApiResponse<Nothing>> {
        log.error("Unhandled exception occurred", exception)

        val errorCode = CommonErrorCode.INTERNAL_SERVER_ERROR

        return ResponseEntity
            .status(errorCode.status)
            .body(ApiResponse.fail(errorCode))
    }
}
