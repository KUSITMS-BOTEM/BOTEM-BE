package com.botem.domain.account.service

import com.botem.domain.account.exception.PayoutAccountErrorCode
import com.botem.global.exception.BusinessException
import com.botem.global.property.PayoutAccountEncryptionProperties
import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@Component
class AccountNumberCipher(
    properties: PayoutAccountEncryptionProperties,
) {

    private val currentKeyId = properties.currentKeyId
    private val legacyKeyId = properties.legacyKeyId
    private val keys = properties.keys.mapValues { (_, key) -> SecretKeySpec(Base64.getDecoder().decode(key), ALGORITHM) }
    private val secureRandom = SecureRandom()

    // 숫자, 공백, 하이픈만 허용하고 숫자만 남긴 8~20자리를 암호화한다
    fun normalizeAndEncrypt(accountNumber: String): String {
        if (!accountNumber.matches(ACCOUNT_NUMBER_INPUT_REGEX)) {
            throw BusinessException(PayoutAccountErrorCode.INVALID_ACCOUNT_NUMBER)
        }

        val normalized = accountNumber.filter(Char::isDigit)
        if (normalized.length !in ACCOUNT_NUMBER_LENGTH) {
            throw BusinessException(PayoutAccountErrorCode.INVALID_ACCOUNT_NUMBER)
        }

        return encrypt(normalized)
    }

    fun encrypt(accountNumber: String): String {
        val iv = ByteArray(IV_LENGTH).also(secureRandom::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keyOf(currentKeyId), GCMParameterSpec(TAG_LENGTH, iv))
        val encrypted = cipher.doFinal(accountNumber.toByteArray(Charsets.UTF_8))

        return "$currentKeyId:${Base64.getEncoder().encodeToString(iv + encrypted)}"
    }

    fun decrypt(encryptedAccountNumber: String): String {
        val (keyId, payload) = parseEncryptedAccountNumber(encryptedAccountNumber)

        return runCatching {
            val decoded = Base64.getDecoder().decode(payload)
            require(decoded.size > IV_LENGTH) { "암호화된 계좌번호 형식이 올바르지 않습니다." }

            val iv = decoded.copyOfRange(0, IV_LENGTH)
            val encrypted = decoded.copyOfRange(IV_LENGTH, decoded.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, keyOf(keyId), GCMParameterSpec(TAG_LENGTH, iv))

            cipher.doFinal(encrypted).toString(Charsets.UTF_8)
        }.getOrElse {
            throw IllegalArgumentException("암호화된 계좌번호를 복호화할 수 없습니다.")
        }
    }

    fun isEncryptedWithCurrentKey(encryptedAccountNumber: String): Boolean =
        parseEncryptedAccountNumber(encryptedAccountNumber).first == currentKeyId

    private fun parseEncryptedAccountNumber(encryptedAccountNumber: String): Pair<String, String> {
        val separatorIndex = encryptedAccountNumber.indexOf(KEY_SEPARATOR)
        if (separatorIndex <= 0) {
            return legacyKeyId to encryptedAccountNumber
        }

        return encryptedAccountNumber.substring(0, separatorIndex) to
            encryptedAccountNumber.substring(separatorIndex + 1)
    }

    private fun keyOf(keyId: String): SecretKeySpec =
        keys[keyId] ?: throw IllegalArgumentException("계좌 암호화 키를 찾을 수 없습니다.")

    companion object {
        private const val ALGORITHM = "AES"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_LENGTH = 12
        private const val TAG_LENGTH = 128
        private const val KEY_SEPARATOR = ':'
        private val ACCOUNT_NUMBER_LENGTH = 8..20
        private val ACCOUNT_NUMBER_INPUT_REGEX = Regex("[0-9\\s-]+")
    }
}
