package com.botem.global.property

import jakarta.validation.constraints.NotBlank
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@Validated
@ConfigurationProperties(prefix = "wish-profile")
data class WishProfileProperties(
    @field:NotBlank
    val baseUrl: String,
)
