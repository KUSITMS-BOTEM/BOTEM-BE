package com.botem.global.handler

import com.botem.global.exception.BusinessException
import com.botem.global.exception.CommonErrorCode
import com.botem.global.response.ApiResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.ErrorResponse
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import tools.jackson.databind.exc.MismatchedInputException

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

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatchException(
        exception: MethodArgumentTypeMismatchException,
    ): ResponseEntity<ApiResponse<Nothing>> =
        ResponseEntity
            .badRequest()
            .body(ApiResponse.fail(CommonErrorCode.INVALID_INPUT_VALUE, "${exception.name}: 올바르지 않은 값입니다"))

    // 필수 필드 누락, null, 형식 오류로 요청 본문을 읽지 못한 경우. 입력값은 응답에 담지 않고 필드 경로만 알려줌
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleNotReadableException(
        exception: HttpMessageNotReadableException,
    ): ResponseEntity<ApiResponse<Nothing>> {
        val fieldPath = (exception.cause as? MismatchedInputException)?.path
            ?.joinToString(".") { it.propertyName ?: "[${it.index}]" }
            ?.takeIf(String::isNotEmpty)

        return ResponseEntity
            .badRequest()
            .body(ApiResponse.fail(CommonErrorCode.INVALID_INPUT_VALUE, fieldPath?.let { "$it: 올바르지 않은 값입니다" }))
    }

    @ExceptionHandler(Exception::class)
    fun handleException(exception: Exception): ResponseEntity<ApiResponse<Nothing>> {
        // 없는 주소·지원하지 않는 메서드처럼 Spring이 상태 코드를 정해 둔 요청 오류는 서버 오류가 아니므로
        // 그 상태 코드로 응답하고 에러 로그(Sentry)를 남기지 않는다
        if (exception is ErrorResponse && exception.statusCode.is4xxClientError) {
            return handleClientError(exception)
        }

        log.error("Unhandled exception occurred", exception)

        val errorCode = CommonErrorCode.INTERNAL_SERVER_ERROR

        return ResponseEntity
            .status(errorCode.status)
            .body(ApiResponse.fail(errorCode))
    }

    private fun handleClientError(exception: ErrorResponse): ResponseEntity<ApiResponse<Nothing>> {
        val errorCode = when (exception.statusCode.value()) {
            HttpStatus.NOT_FOUND.value() -> CommonErrorCode.NOT_FOUND
            HttpStatus.METHOD_NOT_ALLOWED.value() -> CommonErrorCode.METHOD_NOT_ALLOWED
            HttpStatus.UNSUPPORTED_MEDIA_TYPE.value() -> CommonErrorCode.UNSUPPORTED_MEDIA_TYPE
            else -> CommonErrorCode.INVALID_INPUT_VALUE
        }

        // 405의 Allow처럼 클라이언트가 참고할 헤더는 그대로 전달한다
        return ResponseEntity
            .status(exception.statusCode)
            .headers(exception.headers)
            .body(ApiResponse.fail(errorCode))
    }
}
