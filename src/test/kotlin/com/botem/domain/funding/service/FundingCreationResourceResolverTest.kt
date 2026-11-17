package com.botem.domain.funding.service

import com.botem.domain.account.entity.AccountVerificationStatus
import com.botem.domain.account.entity.Bank
import com.botem.domain.account.entity.PayoutAccount
import com.botem.domain.account.exception.PayoutAccountErrorCode
import com.botem.domain.account.repository.PayoutAccountRepository
import com.botem.domain.funding.entity.FundingStatus
import com.botem.domain.funding.repository.FundingRepository
import com.botem.domain.wish.entity.Wish
import com.botem.domain.wish.exception.WishErrorCode
import com.botem.domain.wish.repository.WishRepository
import com.botem.global.exception.BusinessException
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FundingCreationResourceResolverTest {

    private val wishRepository = mock(WishRepository::class.java)
    private val payoutAccountRepository = mock(PayoutAccountRepository::class.java)
    private val fundingRepository = mock(FundingRepository::class.java)
    private val resolver = FundingCreationResourceResolver(
        wishRepository = wishRepository,
        payoutAccountRepository = payoutAccountRepository,
        fundingRepository = fundingRepository,
    )

    @Test
    fun `본인 위시가 진행 중 펀딩에 사용되지 않으면 반환한다`() {
        val wish = wish()
        given(wishRepository.findByIdAndUserIdForUpdate(WISH_ID, USER_ID)).willReturn(wish)
        given(fundingRepository.existsByOwnerIdAndProductSourceWishIdAndStatusNotIn(USER_ID, WISH_ID, ENDED_STATUSES))
            .willReturn(false)

        assertEquals(wish, resolver.resolveWish(USER_ID, WISH_ID))
    }

    @Test
    fun `본인 위시가 아니거나 진행 중 펀딩에서 사용 중이면 거부한다`() {
        given(wishRepository.findByIdAndUserIdForUpdate(WISH_ID, USER_ID)).willReturn(null)
        val notFound = assertFailsWith<BusinessException> { resolver.resolveWish(USER_ID, WISH_ID) }
        assertEquals(WishErrorCode.WISH_NOT_FOUND, notFound.errorCode)

        given(wishRepository.findByIdAndUserIdForUpdate(WISH_ID, USER_ID)).willReturn(wish())
        given(fundingRepository.existsByOwnerIdAndProductSourceWishIdAndStatusNotIn(USER_ID, WISH_ID, ENDED_STATUSES))
            .willReturn(true)
        val inFunding = assertFailsWith<BusinessException> { resolver.resolveWish(USER_ID, WISH_ID) }
        assertEquals(WishErrorCode.WISH_IN_FUNDING, inFunding.errorCode)
    }

    @Test
    fun `계좌 ID가 없으면 기본 계좌를 반환한다`() {
        val account = payoutAccount()
        given(payoutAccountRepository.findDefaultByUserIdForUpdate(USER_ID)).willReturn(account)

        assertEquals(account, resolver.resolvePayoutAccount(USER_ID, null))
    }

    @Test
    fun `선택한 본인 계좌를 반환한다`() {
        val account = payoutAccount()
        given(payoutAccountRepository.findByIdAndUserIdForUpdate(ACCOUNT_ID, USER_ID)).willReturn(account)

        assertEquals(account, resolver.resolvePayoutAccount(USER_ID, ACCOUNT_ID))
    }

    @Test
    fun `계좌가 없거나 사용할 수 없는 상태이면 거부한다`() {
        given(payoutAccountRepository.findDefaultByUserIdForUpdate(USER_ID)).willReturn(null)
        val notFound = assertFailsWith<BusinessException> { resolver.resolvePayoutAccount(USER_ID, null) }
        assertEquals(PayoutAccountErrorCode.PAYOUT_ACCOUNT_NOT_FOUND, notFound.errorCode)

        val failed = payoutAccount().also { setField(it, "verificationStatus", AccountVerificationStatus.FAILED) }
        given(payoutAccountRepository.findByIdAndUserIdForUpdate(ACCOUNT_ID, USER_ID)).willReturn(failed)
        val notAvailable = assertFailsWith<BusinessException> {
            resolver.resolvePayoutAccount(USER_ID, ACCOUNT_ID)
        }
        assertEquals(PayoutAccountErrorCode.PAYOUT_ACCOUNT_NOT_AVAILABLE, notAvailable.errorCode)
    }

    private fun wish(): Wish =
        Wish(
            userId = USER_ID,
            productName = "무선 헤드폰",
            productImageUrl = "https://image.example.com/product.jpg",
            price = 100_000L,
            productUrl = "https://shop.example.com/product",
        )

    private fun payoutAccount(): PayoutAccount =
        PayoutAccount(
            userId = USER_ID,
            bank = Bank.KAKAO_BANK,
            encryptedAccountNumber = "encrypted-account-number",
            accountHolder = "지유",
            isDefault = true,
        )

    private fun setField(target: Any, fieldName: String, value: Any?) {
        target.javaClass.getDeclaredField(fieldName).apply {
            isAccessible = true
            set(target, value)
        }
    }

    companion object {
        private const val USER_ID = 1L
        private const val WISH_ID = 2L
        private const val ACCOUNT_ID = 3L
        private val ENDED_STATUSES = setOf(FundingStatus.COMPLETED, FundingStatus.CANCELLED)
    }
}
