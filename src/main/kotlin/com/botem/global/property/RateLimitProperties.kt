package com.botem.global.property

import jakarta.validation.constraints.Positive
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

// 비로그인으로 호출할 수 있는 공유 화면 API의 IP당 분당 요청 한도
@Validated
@ConfigurationProperties(prefix = "rate-limit")
data class RateLimitProperties(
    @field:Positive
    val contributionPerMinute: Int = 10,

    @field:Positive
    val attachmentUploadUrlPerMinute: Int = 20,

    @field:Positive
    val guestAccessPerMinute: Int = 10,
)
