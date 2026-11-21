package com.botem.domain.account.service

import com.botem.domain.account.repository.PayoutAccountRepository
import com.botem.domain.account.dto.response.PayoutAccountEncryptionRotationResponse
import com.botem.domain.user.repository.UserRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PayoutAccountEncryptionRotationService(
    private val payoutAccountRepository: PayoutAccountRepository,
    private val userRepository: UserRepository,
    private val accountNumberCipher: AccountNumberCipher,
) {

    // 소유자 잠금은 계좌 수정·대표계좌 변경·탈퇴와 같은 순서로 잡아, 재암호화가 최신 계좌 상태를 덮어쓰지 않게 한다.
    @Transactional
    fun rotate(afterAccountId: Long?, batchSize: Int): PayoutAccountEncryptionRotationResponse {
        require(batchSize in MIN_BATCH_SIZE..MAX_BATCH_SIZE) { "batchSize는 $MIN_BATCH_SIZE~$MAX_BATCH_SIZE 사이여야 합니다." }

        val accountIds = payoutAccountRepository.findIdsAfter(
            afterAccountId = afterAccountId ?: INITIAL_CURSOR,
            pageable = PageRequest.of(0, batchSize + 1),
        )
        val hasNext = accountIds.size > batchSize
        val targetAccountIds = accountIds.take(batchSize)
        if (targetAccountIds.isEmpty()) {
            return PayoutAccountEncryptionRotationResponse(processedCount = 0, rotatedCount = 0, nextAfterAccountId = null)
        }

        payoutAccountRepository.findUserIdsByIdIn(targetAccountIds)
            .distinct()
            .sorted()
            .forEach(userRepository::findByIdForUpdate)
        val accounts = payoutAccountRepository.findAllByIdInForUpdate(targetAccountIds)

        var rotatedCount = 0
        accounts.forEach { account ->
            if (!accountNumberCipher.isEncryptedWithCurrentKey(account.encryptedAccountNumber)) {
                account.rotateEncryption(accountNumberCipher.encrypt(accountNumberCipher.decrypt(account.encryptedAccountNumber)))
                rotatedCount++
            }
        }

        return PayoutAccountEncryptionRotationResponse(
            processedCount = accounts.size,
            rotatedCount = rotatedCount,
            nextAfterAccountId = if (hasNext) targetAccountIds.last() else null,
        )
    }

    companion object {
        private const val INITIAL_CURSOR = 0L
        private const val MIN_BATCH_SIZE = 1
        private const val MAX_BATCH_SIZE = 500
    }
}
