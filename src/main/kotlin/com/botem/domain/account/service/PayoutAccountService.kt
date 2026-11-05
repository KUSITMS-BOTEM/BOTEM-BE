package com.botem.domain.account.service

import com.botem.domain.account.dto.request.SavePayoutAccountRequest
import com.botem.domain.account.dto.response.BankResponse
import com.botem.domain.account.dto.response.PayoutAccountResponse
import com.botem.domain.account.entity.Bank
import com.botem.domain.account.entity.PayoutAccount
import com.botem.domain.account.exception.PayoutAccountErrorCode
import com.botem.domain.account.repository.PayoutAccountRepository
import com.botem.domain.user.exception.UserErrorCode
import com.botem.domain.user.repository.UserRepository
import com.botem.global.exception.BusinessException
import com.botem.global.exception.CommonErrorCode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PayoutAccountService(
    private val payoutAccountRepository: PayoutAccountRepository,
    private val userRepository: UserRepository,
    private val accountNumberCipher: AccountNumberCipher,
) {

    @Transactional(readOnly = true)
    fun getPayoutAccounts(userId: Long): List<PayoutAccountResponse> =
        payoutAccountRepository.findAllByUserIdOrderByCreatedAtAsc(userId)
            .sortedByDescending(PayoutAccount::isDefault)
            .map(::toResponse)

    fun getSupportedBanks(): List<BankResponse> =
        Bank.entries.map(BankResponse::from)

    @Transactional
    fun createPayoutAccount(userId: Long, request: SavePayoutAccountRequest): PayoutAccountResponse {
        lockUser(userId)
        val accounts = payoutAccountRepository.findAllByUserIdOrderByCreatedAtAsc(userId)

        if (accounts.size >= MAX_ACCOUNT_COUNT) {
            throw BusinessException(PayoutAccountErrorCode.MAX_ACCOUNT_COUNT_EXCEEDED)
        }
        validateDuplicateBank(accounts, request.bank)

        val account = payoutAccountRepository.save(
            PayoutAccount(
                userId = userId,
                bank = request.bank,
                encryptedAccountNumber = accountNumberCipher.encrypt(normalizeAccountNumber(request.accountNumber)),
                accountHolder = normalizeAccountHolder(request.accountHolder),
                isDefault = accounts.isEmpty(),
            ),
        )

        return toResponse(account)
    }

    @Transactional
    fun updatePayoutAccount(
        userId: Long,
        accountId: Long,
        request: SavePayoutAccountRequest,
    ): PayoutAccountResponse {
        lockUser(userId)
        val accounts = payoutAccountRepository.findAllByUserIdOrderByCreatedAtAsc(userId)
        val account = findOwnedAccount(accounts, accountId)

        validateDuplicateBank(accounts, request.bank, accountId)
        account.update(
            bank = request.bank,
            encryptedAccountNumber = accountNumberCipher.encrypt(normalizeAccountNumber(request.accountNumber)),
            accountHolder = normalizeAccountHolder(request.accountHolder),
        )

        return toResponse(account)
    }

    @Transactional
    fun designateDefaultAccount(userId: Long, accountId: Long): PayoutAccountResponse {
        lockUser(userId)
        val accounts = payoutAccountRepository.findAllByUserIdOrderByCreatedAtAsc(userId)
        val target = findOwnedAccount(accounts, accountId)

        accounts.forEach { account ->
            if (account.id == target.id) {
                account.designateAsDefault()
            } else {
                account.releaseDefault()
            }
        }

        return toResponse(target)
    }

    private fun lockUser(userId: Long) {
        userRepository.findByIdForUpdate(userId)
            ?: throw BusinessException(UserErrorCode.USER_NOT_FOUND)
    }

    private fun validateDuplicateBank(
        accounts: List<PayoutAccount>,
        bank: Bank,
        excludedAccountId: Long? = null,
    ) {
        if (accounts.any { it.bank == bank && it.id != excludedAccountId }) {
            throw BusinessException(PayoutAccountErrorCode.BANK_ALREADY_REGISTERED)
        }
    }

    private fun findOwnedAccount(accounts: List<PayoutAccount>, accountId: Long): PayoutAccount =
        accounts.find { it.id == accountId }
            ?: throw BusinessException(PayoutAccountErrorCode.PAYOUT_ACCOUNT_NOT_FOUND)

    private fun normalizeAccountNumber(accountNumber: String): String {
        if (!accountNumber.matches(ACCOUNT_NUMBER_INPUT_REGEX)) {
            throw BusinessException(PayoutAccountErrorCode.INVALID_ACCOUNT_NUMBER)
        }

        val normalized = accountNumber.filter(Char::isDigit)
        if (normalized.length !in ACCOUNT_NUMBER_LENGTH) {
            throw BusinessException(PayoutAccountErrorCode.INVALID_ACCOUNT_NUMBER)
        }

        return normalized
    }

    private fun normalizeAccountHolder(accountHolder: String?): String? {
        val normalized = accountHolder
            ?.trim { it.isWhitespace() || Character.isSpaceChar(it) }
            ?.takeIf(String::isNotEmpty)

        if (normalized != null && normalized.length > MAX_ACCOUNT_HOLDER_LENGTH) {
            throw BusinessException(
                CommonErrorCode.INVALID_INPUT_VALUE,
                "accountHolder: ${MAX_ACCOUNT_HOLDER_LENGTH}자 이하여야 합니다",
            )
        }

        return normalized
    }

    private fun toResponse(account: PayoutAccount): PayoutAccountResponse =
        PayoutAccountResponse.from(
            account = account,
            accountNumber = accountNumberCipher.decrypt(account.encryptedAccountNumber),
        )

    companion object {
        private const val MAX_ACCOUNT_COUNT = 2
        private const val MAX_ACCOUNT_HOLDER_LENGTH = 100
        private val ACCOUNT_NUMBER_LENGTH = 8..20
        private val ACCOUNT_NUMBER_INPUT_REGEX = Regex("[0-9\\s-]+")
    }
}
