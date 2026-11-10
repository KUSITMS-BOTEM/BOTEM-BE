package com.botem.domain.funding.repository

import com.botem.domain.account.entity.Bank
import com.botem.domain.account.entity.PayoutAccount
import com.botem.domain.account.repository.PayoutAccountRepository
import com.botem.domain.funding.entity.Contribution
import com.botem.domain.funding.entity.ContributionStatus
import com.botem.domain.funding.entity.Funding
import com.botem.domain.funding.entity.FundingPayoutAccountSnapshot
import com.botem.domain.funding.entity.FundingProductSnapshot
import com.botem.domain.funding.entity.FundingStatus
import com.botem.domain.funding.entity.FundingType
import com.botem.domain.funding.entity.GiftVerification
import com.botem.domain.funding.entity.GiftVerificationStatus
import com.botem.domain.funding.exception.FundingErrorCode
import com.botem.domain.user.entity.User
import com.botem.domain.user.repository.UserRepository
import com.botem.domain.wish.entity.Wish
import com.botem.domain.wish.repository.WishRepository
import com.botem.global.config.JpaAuditingConfig
import com.botem.global.exception.BusinessException
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
        val first = wishRepository.saveAndFlush(wish(user.id, "첫 번째 선물"))
        val second = wishRepository.saveAndFlush(wish(user.id, "두 번째 선물"))
        updateWishCreatedAt(first.id, LocalDateTime.of(2026, 11, 6, 10, 0))
        updateWishCreatedAt(second.id, LocalDateTime.of(2026, 11, 7, 10, 0))
        entityManager.clear()

        val wishes = wishRepository.findAllByUserIdOrderByCreatedAtDesc(user.id)

        assertEquals(listOf(second.id, first.id), wishes.map(Wish::id))
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
    fun `여러 참여 상태를 펀딩별 확정 금액과 확인 대기 정보로 집계한다`() {
        val user = saveUser()
        val account = savePayoutAccount(user.id)
        val wish = wishRepository.saveAndFlush(wish(user.id, "생일 선물"))
        val funding = fundingRepository.saveAndFlush(funding(user.id, account, wish))
        val confirmed = contribution(
            fundingId = funding.id,
            amount = 30_000L,
            status = ContributionStatus.AWAITING_TRANSFER,
        ).apply {
            markTransferSent()
            confirm(LocalDateTime.of(2026, 11, 8, 10, 0))
        }
        val pending = contribution(
            fundingId = funding.id,
            amount = 20_000L,
            status = ContributionStatus.PENDING_CONFIRMATION,
        )
        val cancelled = contribution(
            fundingId = funding.id,
            amount = 10_000L,
            status = ContributionStatus.CANCELLED,
        )
        contributionRepository.saveAllAndFlush(listOf(confirmed, pending, cancelled))

        val summary = contributionRepository.summarizeByFundingIds(listOf(funding.id)).single()

        assertEquals(30_000L, summary.confirmedAmount)
        assertEquals(20_000L, summary.pendingAmount)
        assertEquals(1L, summary.pendingCount)
    }

    @Test
    fun `참여 내역은 확인 완료 상태로 직접 생성할 수 없다`() {
        assertFailsWith<IllegalArgumentException> {
            Contribution(
                fundingId = 1L,
                participantUserId = null,
                participantName = "친구",
                amount = 30_000L,
                status = ContributionStatus.CONFIRMED,
                guestPasswordHash = "hashed-password",
            )
        }
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

    @Test
    fun `사용자가 인증할 선물을 오래된 마감 순으로 조회하고 취소 참여와 완료 인증을 제외한다`() {
        val recipient = saveUser()
        val organizer = saveUser()
        val participant = saveUser()
        val recipientAccount = savePayoutAccount(recipient.id)
        val organizerAccount = savePayoutAccount(organizer.id)
        val recipientWish = wishRepository.saveAndFlush(wish(recipient.id, "생일 선물"))
        val organizerWish = wishRepository.saveAndFlush(wish(organizer.id, "서프라이즈 선물"))
        val birthdayFunding = fundingRepository.saveAndFlush(
            fundingForVerification(
                ownerId = recipient.id,
                recipientId = null,
                type = FundingType.BIRTHDAY,
                deadline = LocalDateTime.of(2026, 10, 1, 23, 59),
                account = recipientAccount,
                wish = recipientWish,
            ),
        )
        val surpriseFunding = fundingRepository.saveAndFlush(
            fundingForVerification(
                ownerId = organizer.id,
                recipientId = recipient.id,
                type = FundingType.SURPRISE,
                deadline = LocalDateTime.of(2026, 10, 2, 23, 59),
                account = organizerAccount,
                wish = organizerWish,
            ),
        )
        val completedFunding = fundingRepository.saveAndFlush(
            fundingForVerification(
                ownerId = recipient.id,
                recipientId = null,
                type = FundingType.BIRTHDAY,
                deadline = LocalDateTime.of(2026, 9, 30, 23, 59),
                account = recipientAccount,
                wish = recipientWish,
            ),
        )
        val unrelatedFunding = fundingRepository.saveAndFlush(
            fundingForVerification(
                ownerId = organizer.id,
                recipientId = null,
                type = FundingType.BIRTHDAY,
                deadline = LocalDateTime.of(2026, 9, 29, 23, 59),
                account = organizerAccount,
                wish = organizerWish,
            ),
        )
        val birthdayVerification = giftVerificationRepository.saveAndFlush(
            GiftVerification(birthdayFunding.id, replacementProductName = "스피커"),
        )
        val surpriseVerification = giftVerificationRepository.saveAndFlush(GiftVerification(surpriseFunding.id))
        val completedVerification = giftVerificationRepository.saveAndFlush(GiftVerification(completedFunding.id))
        giftVerificationRepository.saveAndFlush(GiftVerification(unrelatedFunding.id))
        completedVerification.complete(
            imageUrl = "https://image.example.com/completed.jpg",
            thankYouMessage = null,
            purchasedThroughBotemLink = true,
            completedAt = LocalDateTime.of(2026, 11, 1, 12, 0),
        )
        giftVerificationRepository.flush()

        val confirmed = contribution(
            fundingId = birthdayFunding.id,
            amount = 30_000L,
            status = ContributionStatus.AWAITING_TRANSFER,
            participantUserId = participant.id,
        ).apply {
            markTransferSent()
            confirm(LocalDateTime.of(2026, 10, 1, 12, 0))
        }
        val duplicatedParticipant = contribution(
            fundingId = birthdayFunding.id,
            amount = 5_000L,
            status = ContributionStatus.AWAITING_TRANSFER,
            participantUserId = participant.id,
        )
        val messageOnly = contribution(
            fundingId = birthdayFunding.id,
            amount = 0L,
            status = ContributionStatus.MESSAGE_ONLY,
        )
        val cancelled = contribution(
            fundingId = birthdayFunding.id,
            amount = 10_000L,
            status = ContributionStatus.CANCELLED,
        )
        contributionRepository.saveAllAndFlush(listOf(confirmed, duplicatedParticipant, messageOnly, cancelled))
        entityManager.clear()

        val pendingCount = giftVerificationRepository.countByRecipientAndStatus(
            recipient.id,
            GiftVerificationStatus.PENDING,
        )
        val targets = giftVerificationRepository.findTargetsByRecipientAndStatus(
            recipient.id,
            GiftVerificationStatus.PENDING,
            ContributionStatus.CANCELLED,
        )

        assertEquals(2L, pendingCount)
        assertEquals(
            listOf(birthdayVerification.id, surpriseVerification.id),
            targets.map { it.giftVerificationId },
        )
        assertEquals(2L, targets[0].participantCount)
        assertEquals("스피커", targets[0].replacementProductName)
        assertEquals(0L, targets[1].participantCount)
    }

    @Test
    fun `공개 시각이 지난 수신자의 미완료 서프라이즈를 공개 시각 순으로 조회한다`() {
        val now = LocalDateTime.of(2026, 11, 10, 12, 0)
        val recipient = saveNamedUser(nickname = "지유", name = "이지유")
        val otherRecipient = saveNamedUser(nickname = "다른 친구", name = "다른 친구")
        val namedOrganizer = saveNamedUser(nickname = "민지 닉네임", name = "민지")
        val nicknameOrganizer = saveNamedUser(nickname = "준호", name = null)
        val namedAccount = savePayoutAccount(namedOrganizer.id)
        val nicknameAccount = savePayoutAccount(nicknameOrganizer.id)
        val namedWish = wishRepository.saveAndFlush(wish(namedOrganizer.id, "무선 헤드폰"))
        val nicknameWish = wishRepository.saveAndFlush(wish(nicknameOrganizer.id, "스마트 워치"))

        val oldRevealed = fundingRepository.saveAndFlush(
            fundingForVerification(
                ownerId = namedOrganizer.id,
                recipientId = recipient.id,
                type = FundingType.SURPRISE,
                deadline = LocalDateTime.of(2026, 11, 7, 0, 0),
                account = namedAccount,
                wish = namedWish,
            ),
        )
        val revealedExactlyNow = fundingRepository.saveAndFlush(
            fundingForVerification(
                ownerId = nicknameOrganizer.id,
                recipientId = recipient.id,
                type = FundingType.SURPRISE,
                deadline = LocalDateTime.of(2026, 11, 9, 12, 0),
                account = nicknameAccount,
                wish = nicknameWish,
            ),
        )
        fundingRepository.saveAndFlush(
            fundingForVerification(
                ownerId = namedOrganizer.id,
                recipientId = recipient.id,
                type = FundingType.SURPRISE,
                deadline = LocalDateTime.of(2026, 11, 10, 12, 0),
                account = namedAccount,
                wish = namedWish,
            ),
        )
        val completed = fundingRepository.saveAndFlush(
            fundingForVerification(
                ownerId = namedOrganizer.id,
                recipientId = recipient.id,
                type = FundingType.SURPRISE,
                deadline = LocalDateTime.of(2026, 11, 5, 0, 0),
                account = namedAccount,
                wish = namedWish,
            ),
        )
        val cancelled = fundingRepository.saveAndFlush(
            fundingForVerification(
                ownerId = namedOrganizer.id,
                recipientId = recipient.id,
                type = FundingType.SURPRISE,
                deadline = LocalDateTime.of(2026, 11, 6, 0, 0),
                account = namedAccount,
                wish = namedWish,
            ),
        )
        fundingRepository.saveAndFlush(
            fundingForVerification(
                ownerId = namedOrganizer.id,
                recipientId = otherRecipient.id,
                type = FundingType.SURPRISE,
                deadline = LocalDateTime.of(2026, 11, 7, 0, 0),
                account = namedAccount,
                wish = namedWish,
            ),
        )
        contributionRepository.saveAllAndFlush(
            listOf(
                contribution(
                    oldRevealed.id,
                    10_000L,
                    ContributionStatus.AWAITING_TRANSFER,
                    participantUserId = namedOrganizer.id,
                ),
                contribution(
                    oldRevealed.id,
                    5_000L,
                    ContributionStatus.PENDING_CONFIRMATION,
                    participantUserId = namedOrganizer.id,
                ),
                contribution(oldRevealed.id, 0L, ContributionStatus.MESSAGE_ONLY),
                contribution(oldRevealed.id, 5_000L, ContributionStatus.CANCELLED),
            ),
        )
        updateFundingStatus(completed.id, FundingStatus.COMPLETED)
        updateFundingStatus(cancelled.id, FundingStatus.CANCELLED)
        entityManager.clear()

        val cards = fundingRepository.findRevealedSurpriseCards(recipient.id, now)

        assertEquals(listOf(oldRevealed.id, revealedExactlyNow.id), cards.map { it.fundingId })
        assertEquals("민지", cards[0].organizerName)
        assertEquals(2L, cards[0].participantCount)
        assertEquals("준호", cards[1].organizerName)
        assertEquals(0L, cards[1].participantCount)
        assertEquals(now, cards[1].revealAt)
    }

    @Test
    fun `보탬 링크 구매를 확인하지 않으면 선물 인증을 완료할 수 없다`() {
        val verification = GiftVerification(fundingId = 1L)

        val exception = assertFailsWith<BusinessException> {
            verification.complete(
                imageUrl = "https://image.example.com/gift.jpg",
                thankYouMessage = null,
                purchasedThroughBotemLink = false,
                completedAt = LocalDateTime.of(2026, 11, 7, 13, 0),
            )
        }

        assertEquals(FundingErrorCode.GIFT_PURCHASE_CONFIRMATION_REQUIRED, exception.errorCode)
    }

    private fun saveUser(): User =
        userRepository.saveAndFlush(
            User(
                kakaoId = System.nanoTime(),
                nickname = "지유",
                profileImageUrl = null,
            ),
        )

    private fun saveNamedUser(
        nickname: String,
        name: String?,
    ): User =
        userRepository.saveAndFlush(
            User(
                kakaoId = System.nanoTime(),
                nickname = nickname,
                profileImageUrl = null,
                name = name,
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

    private fun contribution(
        fundingId: Long,
        amount: Long,
        status: ContributionStatus,
        participantUserId: Long? = null,
    ): Contribution =
        Contribution(
            fundingId = fundingId,
            participantUserId = participantUserId,
            participantName = "친구",
            amount = amount,
            status = status,
            guestPasswordHash = if (participantUserId == null) "hashed-password-$amount" else null,
        )

    private fun wish(userId: Long, productName: String): Wish =
        Wish(
            userId = userId,
            productName = productName,
            productImageUrl = "https://image.example.com/product.jpg",
            price = 50_000L,
            productUrl = "https://shop.example.com/product",
        )

    private fun updateWishCreatedAt(wishId: Long, createdAt: LocalDateTime) {
        entityManager.createNativeQuery("UPDATE wishes SET created_at = :createdAt WHERE id = :wishId")
            .setParameter("createdAt", createdAt)
            .setParameter("wishId", wishId)
            .executeUpdate()
    }

    private fun updateFundingStatus(fundingId: Long, status: FundingStatus) {
        entityManager.createNativeQuery("UPDATE fundings SET status = :status WHERE id = :fundingId")
            .setParameter("status", status.name)
            .setParameter("fundingId", fundingId)
            .executeUpdate()
    }

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

    private fun fundingForVerification(
        ownerId: Long,
        recipientId: Long?,
        type: FundingType,
        deadline: LocalDateTime,
        account: PayoutAccount,
        wish: Wish,
    ): Funding =
        Funding(
            ownerId = ownerId,
            recipientId = recipientId,
            type = type,
            title = "선물 펀딩",
            targetAmount = 50_000L,
            deadline = deadline,
            revealAt = if (type == FundingType.SURPRISE) deadline.plusDays(1) else null,
            story = null,
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
