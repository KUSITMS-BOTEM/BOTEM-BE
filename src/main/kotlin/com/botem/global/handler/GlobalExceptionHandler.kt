package com.botem.global.handler

import com.botem.global.exception.BusinessException
import com.botem.global.exception.CommonErrorCode
import com.botem.global.notification.DiscordErrorNotifier
import com.botem.global.response.ApiResponse
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ResponseEntity
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.ServletWebRequest
import org.springframework.web.context.request.WebRequest
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

@RestControllerAdvice
class GlobalExceptionHandler(
    private val discordErrorNotifier: DiscordErrorNotifier,
) : ResponseEntityExceptionHandler() {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(BusinessException::class)
    fun handleBusinessException(
        exception: BusinessException,
        request: HttpServletRequest,
    ): ResponseEntity<ApiResponse<Nothing>> {
        val errorCode = exception.errorCode

        if (errorCode.status.is5xxServerError) {
            log.error("Business exception with server error occurred", exception)
            discordErrorNotifier.notify(exception, request)
        }

        return ResponseEntity
            .status(errorCode.status)
            .body(ApiResponse.fail(errorCode, exception.detail))
    }

    override fun handleMethodArgumentNotValid(
        ex: MethodArgumentNotValidException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        val detail = ex.bindingResult.allErrors
            .filterIsInstance<FieldError>()
            .joinToString(", ") { "${it.field}: ${it.defaultMessage}" }

        return ResponseEntity
            .badRequest()
            .body(ApiResponse.fail(CommonErrorCode.INVALID_INPUT_VALUE, detail))
    }

    // Spring MVC 표준 예외(404, 405, 400 등)를 원래 상태 코드와 ApiResponse 형식으로 응답
    override fun handleExceptionInternal(
        ex: Exception,
        body: Any?,
        headers: HttpHeaders,
        statusCode: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        val errorCode = when {
            statusCode.value() == HttpStatus.NOT_FOUND.value() -> CommonErrorCode.NOT_FOUND
            statusCode.value() == HttpStatus.METHOD_NOT_ALLOWED.value() -> CommonErrorCode.METHOD_NOT_ALLOWED
            statusCode.is4xxClientError -> CommonErrorCode.INVALID_INPUT_VALUE
            else -> CommonErrorCode.INTERNAL_SERVER_ERROR
        }

        if (statusCode.is5xxServerError) {
            log.error("Spring MVC exception occurred", ex)
            discordErrorNotifier.notify(ex, (request as? ServletWebRequest)?.request)
        }

        return super.handleExceptionInternal(ex, ApiResponse.fail(errorCode), headers, statusCode, request)
    }

    @ExceptionHandler(Exception::class)
    fun handleException(
        exception: Exception,
        request: HttpServletRequest,
    ): ResponseEntity<ApiResponse<Nothing>> {
        log.error("Unhandled exception occurred", exception)
        discordErrorNotifier.notify(exception, request)

        val errorCode = CommonErrorCode.INTERNAL_SERVER_ERROR

        return ResponseEntity
            .status(errorCode.status)
            .body(ApiResponse.fail(errorCode))
    }
}
