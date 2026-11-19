package com.botem.domain.funding.service

import com.botem.domain.account.entity.Bank
import com.botem.domain.account.exception.PayoutAccountErrorCode
import com.botem.domain.account.service.AccountNumberCipher
import com.botem.domain.funding.entity.FundingStatus
import com.botem.domain.funding.repository.FundingRepository
import com.botem.domain.wish.entity.Wish
import com.botem.domain.wish.exception.WishErrorCode
import com.botem.domain.wish.repository.WishRepository
import com.botem.global.exception.BusinessException
import com.botem.global.property.PayoutAccountEncryptionProperties
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class FundingCreationResourceResolverTest {

    private val wishRepository = mock(WishRepository::class.java)
    private val fundingRepository = mock(FundingRepository::class.java)
    private val cipher = AccountNumberCipher(
        PayoutAccountEncryptionProperties(key = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="),
    )
    private val resolver = FundingCreationResourceResolver(
        wishRepository = wishRepository,
        fundingRepository = fundingRepository,
        accountNumberCipher = cipher,
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
    fun `직접 입력한 계좌를 정규화하고 암호화해 스냅샷으로 반환한다`() {
        val snapshot = resolver.resolveDirectPayoutAccount(Bank.TOSS_BANK, "1001-5901-4821")

        assertNull(snapshot.sourcePayoutAccountId)
        assertEquals(Bank.TOSS_BANK, snapshot.bank)
        assertEquals("100159014821", cipher.decrypt(snapshot.encryptedAccountNumber))
        assertNull(snapshot.accountHolder)
    }

    @Test
    fun `직접 입력 계좌번호 형식이 맞지 않으면 거부한다`() {
        val invalidNumber = assertFailsWith<BusinessException> {
            resolver.resolveDirectPayoutAccount(Bank.TOSS_BANK, "1234")
        }
        assertEquals(PayoutAccountErrorCode.INVALID_ACCOUNT_NUMBER, invalidNumber.errorCode)
    }

    private fun wish(): Wish =
        Wish(
            userId = USER_ID,
            productName = "무선 헤드폰",
            productImageUrl = "https://image.example.com/product.jpg",
            price = 100_000L,
            productUrl = "https://shop.example.com/product",
        )

    companion object {
        private const val USER_ID = 1L
        private const val WISH_ID = 2L
        private val ENDED_STATUSES = setOf(FundingStatus.COMPLETED, FundingStatus.CANCELLED)
    }
}
