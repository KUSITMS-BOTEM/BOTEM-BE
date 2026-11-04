package com.botem.global.notification

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

// ponytail: 디스코드 에러 알림 확인용 임시 API, 운영 검증 후 삭제
@Tag(name = "Test", description = "테스트 API")
@RestController
class ErrorAlertTestController {

    @Operation(
        summary = "500 에러 알림 테스트",
        description = "강제로 500 에러를 발생시켜 디스코드 에러 알림을 확인합니다. 로그인이 필요하며, 같은 위치의 에러라 1분에 한 번만 알림이 전송됩니다.",
    )
    @GetMapping("/api/test/error")
    fun throwError(): Nothing =
        throw IllegalStateException("디스코드 에러 알림 테스트", RuntimeException("근본 원인 테스트"))
}
