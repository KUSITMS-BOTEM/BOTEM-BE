package com.botem.domain.funding.repository

import com.botem.domain.account.entity.Bank
import com.botem.domain.account.entity.PayoutAccount
import com.botem.domain.account.repository.PayoutAccountRepository
import com.botem.domain.funding.entity.Contribution
import com.botem.domain.funding.entity.ContributionStatus
import com.botem.domain.funding.entity.Funding
import com.botem.domain.funding.entity.FundingPayoutAccountSnapshot
import com.botem.domain.funding.entity.FundingProductSnapshot
import com.botem.domain.funding.entity.FundingType
import com.botem.domain.funding.entity.GiftVerification
import com.botem.domain.funding.entity.GiftVerificationStatus
import com.botem.domain.user.entity.User
import com.botem.domain.user.repository.UserRepository
import com.botem.domain.wish.entity.Wish
import com.botem.domain.wish.repository.WishRepository
import com.botem.global.config.JpaAuditingConfig
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNull

@DataJpaTest
@Import(JpaAuditingConfig::class)
class HomeDomainPersistenceTest {

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var payoutAccountRepository: PayoutAccountRepository

    @Autowired
    private lateinit var wishRepository: WishRepository

    @Autowired
    private lateinit var fundingRepository: FundingRepository

    @Autowired
    private lateinit var contributionRepository: ContributionRepository

    @Autowired
    private lateinit var giftVerificationRepository: GiftVerificationRepository

    @Autowired
    private lateinit var entityManager: EntityManager

    @Test
    fun `위시를 저장하고 사용자별 최신순으로 조회한다`() {
        val user = saveUser()
        val first = wishRepository.save(wish(user.id, "첫 번째 선물"))
        val second = wishRepository.save(wish(user.id, "두 번째 선물"))
        wishRepository.flush()

        val wishes = wishRepository.findAllByUserIdOrderByCreatedAtDesc(user.id)

        assertEquals(setOf(first.id, second.id), wishes.map(Wish::id).toSet())
        assertEquals(2, wishes.size)
    }

    @Test
    fun `펀딩을 저장하면 상품과 받을 계좌 정보가 스냅샷으로 유지된다`() {
        val user = saveUser()
        val account = savePayoutAccount(user.id)
        val wish = wishRepository.saveAndFlush(wish(user.id, "생일 선물"))
        val funding = fundingRepository.saveAndFlush(funding(user.id, account, wish))

        entityManager.clear()

        val saved = fundingRepository.findById(funding.id).orElseThrow()
        assertEquals(wish.id, saved.product.sourceWishId)
        assertEquals("생일 선물", saved.product.name)
        assertEquals(50_000L, saved.product.price)
        assertEquals(account.id, saved.payoutAccount.sourcePayoutAccountId)
        assertEquals(Bank.KAKAO_BANK, saved.payoutAccount.bank)
        assertEquals("encrypted-account-number", saved.payoutAccount.encryptedAccountNumber)
    }

    @Test
    fun `참여 내역의 입금 확인 상태와 확인 시각을 저장한다`() {
        val user = saveUser()
        val account = savePayoutAccount(user.id)
        val wish = wishRepository.saveAndFlush(wish(user.id, "생일 선물"))
        val funding = fundingRepository.saveAndFlush(funding(user.id, account, wish))
        val contribution = contributionRepository.save(
            Contribution(
                fundingId = funding.id,
                participantUserId = null,
                participantName = "친구",
                amount = 30_000L,
                status = ContributionStatus.AWAITING_TRANSFER,
                guestPasswordHash = "hashed-password",
            ),
        )
        val confirmedAt = LocalDateTime.of(2026, 11, 7, 12, 0)

        contribution.markTransferSent()
        contribution.confirm(confirmedAt)
        contributionRepository.flush()
        entityManager.clear()

        val saved = contributionRepository.findById(contribution.id).orElseThrow()
        assertEquals(ContributionStatus.CONFIRMED, saved.status)
        assertEquals(confirmedAt, saved.confirmedAt)
        assertEquals(30_000L, saved.amount)
    }

    @Test
    fun `선물 인증을 완료하면 인증 정보와 완료 시각을 저장한다`() {
        val user = saveUser()
        val account = savePayoutAccount(user.id)
        val wish = wishRepository.saveAndFlush(wish(user.id, "생일 선물"))
        val funding = fundingRepository.saveAndFlush(funding(user.id, account, wish))
        val verification = giftVerificationRepository.saveAndFlush(GiftVerification(funding.id))
        val completedAt = LocalDateTime.of(2026, 11, 7, 13, 0)

        assertEquals(GiftVerificationStatus.PENDING, verification.status)
        assertNull(verification.completedAt)

        verification.complete(
            imageUrl = "https://image.example.com/gift.jpg",
            thankYouMessage = "함께 보태줘서 고마워!",
            purchasedThroughBotemLink = true,
            completedAt = completedAt,
        )
        giftVerificationRepository.flush()
        entityManager.clear()

        val saved = giftVerificationRepository.findByFundingId(funding.id)!!
        assertEquals(GiftVerificationStatus.COMPLETED, saved.status)
        assertEquals("https://image.example.com/gift.jpg", saved.imageUrl)
        assertEquals("함께 보태줘서 고마워!", saved.thankYouMessage)
        assertEquals(completedAt, saved.completedAt)
    }

    private fun saveUser(): User =
        userRepository.saveAndFlush(
            User(
                kakaoId = System.nanoTime(),
                nickname = "지유",
                profileImageUrl = null,
            ),
        )

    private fun savePayoutAccount(userId: Long): PayoutAccount =
        payoutAccountRepository.saveAndFlush(
            PayoutAccount(
                userId = userId,
                bank = Bank.KAKAO_BANK,
                encryptedAccountNumber = "encrypted-account-number",
                accountHolder = "지유",
                isDefault = true,
            ),
        )

    private fun wish(userId: Long, productName: String): Wish =
        Wish(
            userId = userId,
            productName = productName,
            productImageUrl = "https://image.example.com/product.jpg",
            price = 50_000L,
            productUrl = "https://shop.example.com/product",
        )

    private fun funding(
        userId: Long,
        account: PayoutAccount,
        wish: Wish,
    ): Funding =
        Funding(
            ownerId = userId,
            recipientId = null,
            type = FundingType.BIRTHDAY,
            title = "생일 선물 펀딩",
            targetAmount = 50_000L,
            deadline = LocalDateTime.of(2026, 12, 1, 23, 59),
            revealAt = null,
            story = "갖고 싶었던 선물이에요.",
            promise = null,
            product = FundingProductSnapshot(
                sourceWishId = wish.id,
                name = wish.productName,
                imageUrl = wish.productImageUrl,
                price = wish.price,
                url = wish.productUrl,
            ),
            payoutAccount = FundingPayoutAccountSnapshot(
                sourcePayoutAccountId = account.id,
                bank = account.bank,
                encryptedAccountNumber = account.encryptedAccountNumber,
                accountHolder = account.accountHolder,
            ),
        )
}
