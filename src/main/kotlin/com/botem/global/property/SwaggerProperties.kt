package com.botem.global.property

import org.springframework.boot.context.properties.ConfigurationProperties

// username이 비어 있으면 Swagger 인증을 적용하지 않음 (local)
@ConfigurationProperties(prefix = "swagger")
data class SwaggerProperties(
    val username: String = "",
    val password: String = "",
)
