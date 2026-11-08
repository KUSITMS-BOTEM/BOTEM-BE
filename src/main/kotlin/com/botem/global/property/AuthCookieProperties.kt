package com.botem.global.property

import jakarta.validation.constraints.Pattern
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@Validated
@ConfigurationProperties(prefix = "auth-cookie")
data class AuthCookieProperties(
    // 로그인 상태 쿠키를 프론트 도메인과 공유하기 위한 Domain. 미지정 시 host-only
    // 잘못된 값은 쿠키 생성 시점(토큰 교체 이후)에 실패하므로 기동 시점에 막는다
    @field:Pattern(
        regexp = "^\\.?([a-z0-9]([a-z0-9-]*[a-z0-9])?\\.)+[a-z]{2,}$",
        message = "쿠키 도메인 형식이 올바르지 않습니다 (예: .botem.site)",
    )
    val domain: String? = null,
)
