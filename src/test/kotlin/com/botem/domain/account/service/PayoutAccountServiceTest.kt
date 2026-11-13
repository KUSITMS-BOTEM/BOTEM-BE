package com.botem.domain.account.service

import com.botem.domain.account.dto.request.SavePayoutAccountRequest
import com.botem.domain.account.entity.AccountVerificationStatus
import com.botem.domain.account.entity.Bank
import com.botem.domain.account.entity.PayoutAccount
import com.botem.domain.account.exception.PayoutAccountErrorCode
import com.botem.domain.account.repository.PayoutAccountRepository
import com.botem.domain.user.entity.User
import com.botem.domain.user.exception.UserErrorCode
import com.botem.domain.user.repository.UserRepository
import com.botem.global.exception.BusinessException
import com.botem.global.exception.CommonErrorCode
import com.botem.global.property.PayoutAccountEncryptionProperties
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PayoutAccountServiceTest {

    private val payoutAccountRepository = mock(PayoutAccountRepository::class.java)
    private val userRepository = mock(UserRepository::class.java)
    private val cipher = AccountNumberCipher(
        PayoutAccountEncryptionProperties(
            key = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        ),
    )
    private val service = PayoutAccountService(
        payoutAccountRepository = payoutAccountRepository,
        userRepository = userRepository,
        accountNumberCipher = cipher,
    )
    private val user = User(
        kakaoId = 1234L,
        nickname = "지유",
        profileImageUrl = null,
    )

    @BeforeEach
    fun setUp() {
        `when`(userRepository.findByIdForUpdate(1L)).thenReturn(user)
        `when`(payoutAccountRepository.save(any(PayoutAccount::class.java))).thenAnswer { invocation ->
            invocation.arguments[0] as PayoutAccount
        }
    }

    @Test
    fun `첫 받을 계좌를 등록하면 기본 계좌와 확인 전 상태가 된다`() {
        `when`(payoutAccountRepository.findAllByUserIdOrderByCreatedAtAsc(1L)).thenReturn(emptyList())
        val request = SavePayoutAccountRequest(
            bank = Bank.TOSS_BANK,
            accountNumber = "1000-1234 5678",
            accountHolder = " 지유 ",
        )

        val response = service.createPayoutAccount(1L, request)

        assertEquals(Bank.TOSS_BANK, response.bank)
        assertEquals("토스뱅크", response.bankName)
        assertEquals("100012345678", response.accountNumber)
        assertEquals("지유", response.accountHolder)
        assertTrue(response.isDefault)
        assertEquals(AccountVerificationStatus.UNVERIFIED, response.verificationStatus)
    }

    @Test
    fun `두 번째 받을 계좌는 기본 계좌가 아니다`() {
        val first = account(bank = Bank.TOSS_BANK, isDefault = true)
        `when`(payoutAccountRepository.findAllByUserIdOrderByCreatedAtAsc(1L)).thenReturn(listOf(first))

        val response = service.createPayoutAccount(
            1L,
            SavePayoutAccountRequest(Bank.KAKAO_BANK, "3333-12-1234567"),
        )

        assertFalse(response.isDefault)
    }

    @Test
    fun `받을 계좌는 최대 두 개까지만 등록할 수 있다`() {
        val accounts = listOf(
            account(bank = Bank.TOSS_BANK, isDefault = true),
            account(bank = Bank.KAKAO_BANK, isDefault = false),
        )
        `when`(payoutAccountRepository.findAllByUserIdOrderByCreatedAtAsc(1L)).thenReturn(accounts)

        val exception = assertFailsWith<BusinessException> {
            service.createPayoutAccount(
                1L,
                SavePayoutAccountRequest(Bank.SHINHAN, "110-123-456789"),
            )
        }

        assertEquals(PayoutAccountErrorCode.MAX_ACCOUNT_COUNT_EXCEEDED, exception.errorCode)
    }

    @Test
    fun `동일한 은행은 중복 등록할 수 없다`() {
        val existing = account(bank = Bank.TOSS_BANK, isDefault = true)
        `when`(payoutAccountRepository.findAllByUserIdOrderByCreatedAtAsc(1L)).thenReturn(listOf(existing))

        val exception = assertFailsWith<BusinessException> {
            service.createPayoutAccount(
                1L,
                SavePayoutAccountRequest(Bank.TOSS_BANK, "1000-9999-9999"),
            )
        }

        assertEquals(PayoutAccountErrorCode.BANK_ALREADY_REGISTERED, exception.errorCode)
    }

    @Test
    fun `계좌를 수정하면 계좌번호를 다시 암호화하고 확인 전 상태로 초기화한다`() {
        val existing = account(id = 10L, bank = Bank.TOSS_BANK, isDefault = true)
        val previousEncryptedNumber = existing.encryptedAccountNumber
        `when`(payoutAccountRepository.findAllByUserIdOrderByCreatedAtAsc(1L)).thenReturn(listOf(existing))

        val response = service.updatePayoutAccount(
            userId = 1L,
            accountId = 10L,
            request = SavePayoutAccountRequest(
                bank = Bank.KB_KOOKMIN,
                accountNumber = "123-456-789012",
                accountHolder = null,
            ),
        )

        assertEquals(Bank.KB_KOOKMIN, response.bank)
        assertEquals("123456789012", response.accountNumber)
        assertEquals(AccountVerificationStatus.UNVERIFIED, response.verificationStatus)
        assertNotEquals(previousEncryptedNumber, existing.encryptedAccountNumber)
        assertTrue(response.isDefault)
    }

    @Test
    fun `다른 계좌를 기본으로 지정하면 기존 기본 계좌를 해제한다`() {
        val first = account(id = 10L, bank = Bank.TOSS_BANK, isDefault = true)
        val second = account(id = 20L, bank = Bank.KAKAO_BANK, isDefault = false)
        `when`(payoutAccountRepository.findAllByUserIdOrderByCreatedAtAsc(1L))
            .thenReturn(listOf(first, second))

        val response = service.designateDefaultAccount(1L, 20L)

        assertFalse(first.isDefault)
        assertTrue(second.isDefault)
        assertEquals(Bank.KAKAO_BANK, response.bank)
    }

    @Test
    fun `다른 사용자의 계좌는 수정할 수 없다`() {
        val otherUsersAccount = account(id = 20L, userId = 2L, bank = Bank.KAKAO_BANK, isDefault = true)
        `when`(payoutAccountRepository.findAllByUserIdOrderByCreatedAtAsc(1L)).thenReturn(emptyList())

        val exception = assertFailsWith<BusinessException> {
            service.updatePayoutAccount(
                userId = 1L,
                accountId = otherUsersAccount.id,
                request = SavePayoutAccountRequest(Bank.KAKAO_BANK, "3333-12-1234567"),
            )
        }

        assertEquals(PayoutAccountErrorCode.PAYOUT_ACCOUNT_NOT_FOUND, exception.errorCode)
    }

    @Test
    fun `숫자 공백 하이픈 외의 문자가 포함된 계좌번호는 등록할 수 없다`() {
        `when`(payoutAccountRepository.findAllByUserIdOrderByCreatedAtAsc(1L)).thenReturn(emptyList())

        val exception = assertFailsWith<BusinessException> {
            service.createPayoutAccount(
                1L,
                SavePayoutAccountRequest(Bank.TOSS_BANK, "1000-ABCD-5678"),
            )
        }

        assertEquals(PayoutAccountErrorCode.INVALID_ACCOUNT_NUMBER, exception.errorCode)
    }

    @Test
    fun `받을 계좌 목록은 기본 계좌를 먼저 보여주고 계좌번호를 복호화한다`() {
        val older = PayoutAccount(
            userId = 1L,
            bank = Bank.TOSS_BANK,
            encryptedAccountNumber = cipher.encrypt("100012345678"),
            accountHolder = null,
            isDefault = false,
        )
        val newerDefault = PayoutAccount(
            userId = 1L,
            bank = Bank.KAKAO_BANK,
            encryptedAccountNumber = cipher.encrypt("333312345678"),
            accountHolder = null,
            isDefault = true,
        )
        `when`(payoutAccountRepository.findAllByUserIdOrderByCreatedAtAsc(1L)).thenReturn(listOf(older, newerDefault))

        val accounts = service.getPayoutAccounts(1L)

        assertEquals(listOf(Bank.KAKAO_BANK, Bank.TOSS_BANK), accounts.map { it.bank })
        assertEquals(listOf("333312345678", "100012345678"), accounts.map { it.accountNumber })
    }

    @Test
    fun `존재하지 않는 회원은 받을 계좌를 등록할 수 없다`() {
        val exception = assertFailsWith<BusinessException> {
            service.createPayoutAccount(2L, SavePayoutAccountRequest(bank = Bank.TOSS_BANK, accountNumber = "100012345678"))
        }

        assertEquals(UserErrorCode.USER_NOT_FOUND, exception.errorCode)
    }

    @Test
    fun `예금주가 너무 길면 등록할 수 없다`() {
        `when`(payoutAccountRepository.findAllByUserIdOrderByCreatedAtAsc(1L)).thenReturn(emptyList())

        val exception = assertFailsWith<BusinessException> {
            service.createPayoutAccount(
                1L,
                SavePayoutAccountRequest(bank = Bank.TOSS_BANK, accountNumber = "100012345678", accountHolder = "가".repeat(101)),
            )
        }

        assertEquals(CommonErrorCode.INVALID_INPUT_VALUE, exception.errorCode)
    }

    @Test
    fun `지원 은행 목록은 피그마의 주요 은행 순서로 반환한다`() {
        val banks = service.getSupportedBanks()

        assertEquals(Bank.TOSS_BANK, banks.first().code)
        assertEquals(Bank.KAKAO_BANK, banks[1].code)
        assertEquals(Bank.KDB, banks.last().code)
        assertEquals(12, banks.size)
    }

    private fun account(
        id: Long = 0L,
        userId: Long = 1L,
        bank: Bank,
        isDefault: Boolean,
    ): PayoutAccount =
        PayoutAccount(
            userId = userId,
            bank = bank,
            encryptedAccountNumber = cipher.encrypt("100012345678"),
            accountHolder = "지유",
            isDefault = isDefault,
        ).also { account ->
            PayoutAccount::class.java.getDeclaredField("id").apply {
                isAccessible = true
                set(account, id)
            }
        }
}
