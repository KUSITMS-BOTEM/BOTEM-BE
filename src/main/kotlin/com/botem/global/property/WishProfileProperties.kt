package com.botem.global.property

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "wish-profile")
data class WishProfileProperties(
    val baseUrl: String,
)
