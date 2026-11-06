package com.botem.global.property

import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.NotBlank
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated
import java.util.Base64

@Validated
@ConfigurationProperties(prefix = "payout-account.encryption")
data class PayoutAccountEncryptionProperties(
    @field:NotBlank
    val key: String,
) {
    @get:AssertTrue(message = "Base64로 인코딩한 16, 24 또는 32바이트 키여야 합니다")
    val isKeyValid: Boolean
        get() {
            if (key.isBlank()) {
                return true
            }

            val keyLength = runCatching { Base64.getDecoder().decode(key).size }.getOrNull()
                ?: return false

            return keyLength in setOf(16, 24, 32)
        }
}
