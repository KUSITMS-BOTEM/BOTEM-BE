package com.botem.global.property

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "auth-cookie")
data class AuthCookieProperties(
    // 로그인 상태 쿠키를 프론트 도메인과 공유하기 위한 Domain. 미지정 시 host-only
    val domain: String? = null,
)
