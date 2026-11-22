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
    val currentKeyId: String,

    @field:NotBlank
    val legacyKeyId: String,

    val keys: Map<String, String>,
) {
    constructor(key: String) : this(
        currentKeyId = DEFAULT_KEY_ID,
        legacyKeyId = DEFAULT_KEY_ID,
        keys = mapOf(DEFAULT_KEY_ID to key),
    )
    @get:AssertTrue(message = "현재 키·레거시 키가 Key Ring에 있고, 모든 키는 Base64로 인코딩한 16, 24 또는 32바이트 AES 키여야 합니다")
    val isKeyRingValid: Boolean
        get() {
            if (currentKeyId !in keys || legacyKeyId !in keys || keys.isEmpty()) return false

            return keys.all { (keyId, key) ->
                keyId.matches(KEY_ID_PATTERN) && key.isValidAesKey()
            }
        }

    fun keyOf(keyId: String): String =
        keys[keyId] ?: throw IllegalArgumentException("계좌 암호화 키를 찾을 수 없습니다.")

    private fun String.isValidAesKey(): Boolean {
        if (isBlank()) return false

        val keyLength = runCatching { Base64.getDecoder().decode(this).size }.getOrNull()
            ?: return false

        return keyLength in setOf(16, 24, 32)
    }

    companion object {
        private const val DEFAULT_KEY_ID = "v1"
        private val KEY_ID_PATTERN = Regex("[A-Za-z0-9_-]+")
    }
}
