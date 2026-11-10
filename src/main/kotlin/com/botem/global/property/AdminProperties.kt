package com.botem.global.property

import jakarta.validation.constraints.NotBlank
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@Validated
@ConfigurationProperties(prefix = "admin")
data class AdminProperties(
    @field:NotBlank
    val apiKey: String,
)
