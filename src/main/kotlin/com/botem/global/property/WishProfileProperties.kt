package com.botem.global.property

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.AssertTrue
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated
import java.net.URI

@Validated
@ConfigurationProperties(prefix = "wish-profile")
data class WishProfileProperties(
    @field:NotBlank
    val baseUrl: String,
) {
    @get:AssertTrue(message = "http 또는 https 스킴과 host가 필요하며 query와 fragment는 사용할 수 없습니다")
    val isBaseUrlValid: Boolean
        get() {
            if (baseUrl.isBlank()) {
                return true
            }

            val uri = runCatching { URI(baseUrl) }.getOrNull() ?: return false

            return uri.scheme?.lowercase() in setOf("http", "https") &&
                !uri.host.isNullOrBlank() &&
                uri.rawQuery == null &&
                uri.rawFragment == null
        }
}
