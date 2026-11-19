package com.botem.domain.friend.service

import com.botem.domain.account.entity.Bank
import com.botem.domain.friend.exception.FriendErrorCode
import com.botem.domain.friend.repository.FriendshipRepository
import com.botem.domain.funding.entity.Funding
import com.botem.domain.funding.entity.FundingPayoutAccountSnapshot
import com.botem.domain.funding.entity.FundingProductSnapshot
import com.botem.domain.funding.entity.FundingStatus
import com.botem.domain.funding.entity.FundingType
import com.botem.domain.funding.repository.ContributionRepository
import com.botem.domain.funding.repository.FundingRepository
import com.botem.domain.funding.repository.projection.FundingContributionSummary
import com.botem.domain.funding.repository.projection.FundingParticipantPreviewProjection
import com.botem.domain.user.entity.User
import com.botem.domain.user.entity.UserStatus
import com.botem.domain.user.repository.UserRepository
import com.botem.domain.wish.entity.Wish
import com.botem.domain.wish.exception.WishErrorCode
import com.botem.domain.wish.repository.WishRepository
import com.botem.global.exception.BusinessException
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.MonthDay
import java.time.ZoneId
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FriendProfileServiceTest {

    private val friendshipRepository = mock(FriendshipRepository::class.java)
    private val userRepository = mock(UserRepository::class.java)
    private val fundingRepository = mock(FundingRepository::class.java)
    private val contributionRepository = mock(ContributionRepository::class.java)
    private val wishRepository = mock(WishRepository::class.java)
    private val service = FriendProfileService(
        friendshipRepository = friendshipRepository,
        userRepository = userRepository,
        fundingRepository = fundingRepository,
        contributionRepository = contributionRepository,
        wishRepository = wishRepository,
        clock = Clock.fixed(Instant.parse("2026-10-21T00:41:00Z"), ZoneId.of("Asia/Seoul")),
    )

    @Test
    fun `친구 프로필은 서프라이즈를 포함하고 진행 중 펀딩에 쓰인 위시는 뺀다`() {
        val surprise = funding(id = 31L, deadline = LocalDateTime.of(2026, 10, 23, 23, 59))
        given(friendshipRepository.existsByUserIdAndFriendId(VIEWER_ID, TARGET_ID)).willReturn(true)
        given(userRepository.findById(TARGET_ID)).willReturn(Optional.of(target()))
        given(fundingRepository.findProfileFundings(TARGET_ID, NOW, true)).willReturn(listOf(surprise))
        given(contributionRepository.summarizeByFundingIds(listOf(31L)))
            .willReturn(listOf(FundingContributionSummary(31L, 55_500L, 10_000L, 1L, 7L)))
        given(contributionRepository.findParticipantPreviewsByFundingIds(listOf(31L)))
            .willReturn(listOf(preview(31L, "민지"), preview(31L, "서준")))
        given(wishRepository.findAllByUserIdOrderByCreatedAtDesc(TARGET_ID))
            .willReturn(listOf(wish(11L), wish(10L)))
        given(
            fundingRepository.findAllByOwnerIdAndProductSourceWishIdInAndStatusNotIn(
                TARGET_ID,
                listOf(11L, 10L),
                ENDED_STATUSES,
            ),
        ).willReturn(listOf(funding(id = 40L, sourceWishId = 10L)))

        val profile = service.getFriendProfile(VIEWER_ID, TARGET_ID)

        assertEquals("지유", profile.name)
        assertEquals(3L, profile.birthdayDday)
        assertEquals(2L, profile.fundings.single().remainingDays)
        assertEquals("민지", profile.fundings.single().firstParticipantName)
        assertEquals(55, profile.fundings.single().confirmedPercentage)
        assertTrue(profile.wishesVisible)
        assertEquals(1, profile.wishCount)
        assertEquals(listOf(11L), profile.wishes.map { it.wishId })
    }

    @Test
    fun `친구가 아니면 친구 프로필과 위시 상세를 조회할 수 없다`() {
        given(friendshipRepository.existsByUserIdAndFriendId(VIEWER_ID, TARGET_ID)).willReturn(false)

        val profileException = assertFailsWith<BusinessException> { service.getFriendProfile(VIEWER_ID, TARGET_ID) }
        val wishException = assertFailsWith<BusinessException> { service.getFriendWish(VIEWER_ID, TARGET_ID, 11L) }

        assertEquals(FriendErrorCode.FRIEND_NOT_FOUND, profileException.errorCode)
        assertEquals(FriendErrorCode.FRIEND_NOT_FOUND, wishException.errorCode)
        verifyNoInteractions(fundingRepository, wishRepository)
    }

    @Test
    fun `위시 프로필은 비로그인에게 위시와 서프라이즈를 숨긴다`() {
        givenWishProfileTarget()

        val profile = service.getWishProfile(null, WISH_PROFILE_KEY)

        assertFalse(profile.wishesVisible)
        assertEquals(1, profile.wishCount)
        assertEquals(emptyList(), profile.wishes)
        verify(fundingRepository).findProfileFundings(TARGET_ID, NOW, false)
    }

    @Test
    fun `위시 프로필은 친구가 아닌 로그인 방문자에게 서프라이즈만 보여준다`() {
        givenWishProfileTarget()
        given(friendshipRepository.existsByUserIdAndFriendId(VIEWER_ID, TARGET_ID)).willReturn(false)

        val profile = service.getWishProfile(VIEWER_ID, WISH_PROFILE_KEY)

        assertFalse(profile.wishesVisible)
        verify(fundingRepository).findProfileFundings(TARGET_ID, NOW, true)
    }

    @Test
    fun `위시 프로필 주인 본인에게는 위시를 보여주고 받는 서프라이즈는 숨긴다`() {
        givenWishProfileTarget()

        val profile = service.getWishProfile(TARGET_ID, WISH_PROFILE_KEY)

        assertTrue(profile.wishesVisible)
        assertEquals(listOf(11L), profile.wishes.map { it.wishId })
        verify(fundingRepository).findProfileFundings(TARGET_ID, NOW, false)
    }

    @Test
    fun `탈퇴했거나 없는 위시 프로필은 조회할 수 없다`() {
        given(userRepository.findByWishProfileKey(WISH_PROFILE_KEY)).willReturn(target(UserStatus.WITHDRAWN))

        val exception = assertFailsWith<BusinessException> { service.getWishProfile(null, WISH_PROFILE_KEY) }

        assertEquals(FriendErrorCode.WISH_PROFILE_NOT_FOUND, exception.errorCode)
    }

    @Test
    fun `친구 위시 상세는 진행 중 서프라이즈가 있으면 그 펀딩 ID를 반환한다`() {
        given(friendshipRepository.existsByUserIdAndFriendId(VIEWER_ID, TARGET_ID)).willReturn(true)
        given(userRepository.findById(TARGET_ID)).willReturn(Optional.of(target()))
        given(wishRepository.findByIdAndUserId(11L, TARGET_ID)).willReturn(wish(11L))
        given(
            fundingRepository.findFirstByRecipientIdAndTypeAndRevealAtAfterAndStatusNotIn(
                TARGET_ID,
                FundingType.SURPRISE,
                NOW,
                ENDED_STATUSES,
            ),
        ).willReturn(funding(id = 31L))

        assertEquals(31L, service.getFriendWish(VIEWER_ID, TARGET_ID, 11L).activeSurpriseFundingId)

        given(
            fundingRepository.findFirstByRecipientIdAndTypeAndRevealAtAfterAndStatusNotIn(
                TARGET_ID,
                FundingType.SURPRISE,
                NOW,
                ENDED_STATUSES,
            ),
        ).willReturn(null)

        assertNull(service.getFriendWish(VIEWER_ID, TARGET_ID, 11L).activeSurpriseFundingId)
    }

    @Test
    fun `다른 회원의 위시 ID로는 친구 위시 상세를 조회할 수 없다`() {
        given(friendshipRepository.existsByUserIdAndFriendId(VIEWER_ID, TARGET_ID)).willReturn(true)
        given(userRepository.findById(TARGET_ID)).willReturn(Optional.of(target()))
        given(wishRepository.findByIdAndUserId(OTHER_USERS_WISH_ID, TARGET_ID)).willReturn(null)

        val exception = assertFailsWith<BusinessException> {
            service.getFriendWish(VIEWER_ID, TARGET_ID, OTHER_USERS_WISH_ID)
        }

        assertEquals(WishErrorCode.WISH_NOT_FOUND, exception.errorCode)
    }

    @Test
    fun `이름·생일·위시·참여가 없으면 닉네임과 빈 값으로 프로필을 만든다`() {
        val emptyTarget = User(
            kakaoId = 5678L,
            nickname = "카카오닉네임",
            profileImageUrl = null,
        ).also { setField(it, "id", TARGET_ID) }
        given(friendshipRepository.existsByUserIdAndFriendId(VIEWER_ID, TARGET_ID)).willReturn(true)
        given(userRepository.findById(TARGET_ID)).willReturn(Optional.of(emptyTarget))
        given(fundingRepository.findProfileFundings(TARGET_ID, NOW, true)).willReturn(listOf(funding(id = 31L)))

        val profile = service.getFriendProfile(VIEWER_ID, TARGET_ID)

        assertEquals("카카오닉네임", profile.name)
        assertNull(profile.birthday)
        assertNull(profile.birthdayDday)
        assertEquals(0, profile.wishCount)
        assertEquals(0L, profile.fundings.single().participantCount)
        assertNull(profile.fundings.single().firstParticipantName)
        assertEquals(0, profile.fundings.single().confirmedPercentage)
    }

    private fun givenWishProfileTarget() {
        given(userRepository.findByWishProfileKey(WISH_PROFILE_KEY)).willReturn(target())
        given(wishRepository.findAllByUserIdOrderByCreatedAtDesc(TARGET_ID)).willReturn(listOf(wish(11L)))
    }

    private fun target(status: UserStatus = UserStatus.ACTIVE): User =
        User(
            kakaoId = 1234L,
            nickname = "지유닉네임",
            profileImageUrl = null,
            birthday = MonthDay.of(10, 24),
            name = "지유",
            status = status,
        ).also { setField(it, "id", TARGET_ID) }

    private fun wish(id: Long): Wish =
        Wish(
            userId = TARGET_ID,
            productName = "모자",
            productImageUrl = "https://image.example.com/cap.jpg",
            price = 429_000L,
            productUrl = "https://shop.example.com/cap",
        ).also { setField(it, "id", id) }

    private fun funding(
        id: Long,
        deadline: LocalDateTime = NOW.plusDays(2),
        sourceWishId: Long? = null,
    ): Funding =
        Funding(
            ownerId = 99L,
            recipientId = TARGET_ID,
            type = FundingType.SURPRISE,
            title = null,
            targetAmount = 100_000L,
            deadline = deadline,
            revealAt = deadline.plusSeconds(1),
            story = null,
            promise = null,
            product = FundingProductSnapshot(
                sourceWishId = sourceWishId,
                name = "소니 WH-1000XM4",
                imageUrl = "https://image.example.com/product.jpg",
                price = 100_000L,
                url = "https://shop.example.com/product",
            ),
            payoutAccount = FundingPayoutAccountSnapshot(
                sourcePayoutAccountId = null,
                bank = Bank.KAKAO_BANK,
                encryptedAccountNumber = "encrypted",
                accountHolder = null,
            ),
        ).also { setField(it, "id", id) }

    private fun preview(fundingId: Long, name: String): FundingParticipantPreviewProjection =
        object : FundingParticipantPreviewProjection {
            override val fundingId = fundingId
            override val participantUserId: Long? = null
            override val participantName = name
            override val profileImageUrl: String? = null
        }

    private fun setField(target: Any, fieldName: String, value: Any) {
        target.javaClass.getDeclaredField(fieldName).apply {
            isAccessible = true
            set(target, value)
        }
    }

    companion object {
        private const val VIEWER_ID = 1L
        private const val TARGET_ID = 2L
        private const val OTHER_USERS_WISH_ID = 99L
        private const val WISH_PROFILE_KEY = "8f0c6d779fae47f3a979302f2782d20f"
        private val NOW = LocalDateTime.of(2026, 10, 21, 9, 41)
        private val ENDED_STATUSES = setOf(FundingStatus.COMPLETED, FundingStatus.CANCELLED)
    }
}
