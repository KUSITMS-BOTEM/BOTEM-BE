package com.botem.domain.friend.service

import com.botem.domain.account.entity.Bank
import com.botem.domain.friend.exception.FriendErrorCode
import com.botem.domain.friend.repository.FriendRemovalRepository
import com.botem.domain.friend.repository.FriendshipRepository
import com.botem.domain.funding.entity.Funding
import com.botem.domain.funding.entity.FundingPayoutAccountSnapshot
import com.botem.domain.funding.entity.FundingProductSnapshot
import com.botem.domain.funding.entity.FundingType
import com.botem.domain.funding.repository.FundingRepository
import com.botem.domain.user.entity.User
import com.botem.domain.user.repository.UserRepository
import com.botem.domain.user.service.UserWithdrawnEvent
import com.botem.domain.wish.entity.Wish
import com.botem.domain.wish.repository.WishRepository
import com.botem.global.config.DomainServiceTestConfig
import com.botem.global.config.JpaAuditingConfig
import com.botem.global.exception.BusinessException
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.MonthDay
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate

@DataJpaTest
@Import(JpaAuditingConfig::class, DomainServiceTestConfig::class, FriendService::class, FriendServiceTest.FixedClockConfig::class)
class FriendServiceTest {

    @Autowired
    private lateinit var friendService: FriendService

    @Autowired
    private lateinit var friendshipRepository: FriendshipRepository

    @Autowired
    private lateinit var friendRemovalRepository: FriendRemovalRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var wishRepository: WishRepository

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var fundingRepository: FundingRepository

    @Test
    fun `위시 프로필 키로 서로 친구가 되고 다시 맺어도 중복 저장하지 않는다`() {
        val me = saveUser(kakaoId = 1L)
        val friend = saveUser(kakaoId = 2L)

        friendService.addFriend(me.id, checkNotNull(friend.wishProfileKey))
        friendService.addFriend(me.id, checkNotNull(friend.wishProfileKey))
        friendService.addFriend(friend.id, checkNotNull(me.wishProfileKey))

        val friendships = friendshipRepository.findAll()
        assertEquals(2, friendships.size)
        assertEquals(setOf(me.id to friend.id, friend.id to me.id), friendships.map { it.userId to it.friendId }.toSet())
        assertTrue(friendships.all { it.id > 0 })
    }

    @Test
    fun `조회 후 잠금을 기다리는 사이 상대가 탈퇴하면 친구를 맺지 않는다`() {
        val me = saveUser(kakaoId = 1L)
        val friend = saveUser(kakaoId = 2L)
        val key = checkNotNull(friend.wishProfileKey)
        userRepository.findByWishProfileKey(key)
        // 영속성 컨텍스트의 friend는 ACTIVE로 남겨 둔 채 DB만 탈퇴 상태로 바꿔, 다른 트랜잭션의 탈퇴 커밋을 흉내낸다
        jdbcTemplate.update("UPDATE users SET status = 'WITHDRAWN' WHERE id = ?", friend.id)

        val exception = assertFailsWith<BusinessException> { friendService.addFriend(me.id, key) }

        assertEquals(FriendErrorCode.FRIEND_TARGET_NOT_FOUND, exception.errorCode)
        assertEquals(0, friendshipRepository.count())
    }

    @Test
    fun `내 위시 프로필 키면 친구를 맺지 않는다`() {
        val me = saveUser(kakaoId = 1L)

        friendService.addFriend(me.id, checkNotNull(me.wishProfileKey))

        assertEquals(0, friendshipRepository.count())
    }

    @Test
    fun `없는 위시 프로필 키나 탈퇴한 회원과는 친구를 맺을 수 없다`() {
        val me = saveUser(kakaoId = 1L)
        val withdrawn = saveUser(kakaoId = 2L)
        val withdrawnKey = checkNotNull(withdrawn.wishProfileKey)
        withdrawn.withdraw(LocalDateTime.now())

        listOf("unknown-key", withdrawnKey).forEach { key ->
            val exception = assertFailsWith<BusinessException>(key) { friendService.addFriend(me.id, key) }

            assertEquals(FriendErrorCode.FRIEND_TARGET_NOT_FOUND, exception.errorCode)
        }
    }

    @Test
    fun `전체 친구는 이름 가나다순, 곧 생일인 친구는 60일 이내를 생일이 가까운 순으로 조회한다`() {
        val me = saveUser(kakaoId = 1L)
        val todayBirthday = saveUser(kakaoId = 2L, name = "하린", birthday = MonthDay.of(11, 12))
        val in60Days = saveUser(kakaoId = 3L, name = "가영", birthday = MonthDay.of(1, 11))
        val in61Days = saveUser(kakaoId = 4L, name = "다은", birthday = MonthDay.of(1, 12))
        val noBirthday = saveUser(kakaoId = 5L, name = null, nickname = "나연")
        val withdrawn = saveUser(kakaoId = 6L, name = "탈퇴")
        listOf(todayBirthday, in60Days, in61Days, noBirthday, withdrawn).forEach { befriend(me, it) }
        withdrawn.withdraw(LocalDateTime.now())
        repeat(2) { wishRepository.save(wish(in60Days.id)) }

        val response = friendService.getFriends(me.id)

        assertEquals(4, response.totalCount)
        assertEquals(listOf("가영", "나연", "다은", "하린"), response.friends.map { it.name })
        assertEquals(listOf("하린", "가영"), response.upcomingBirthdayFriends.map { it.name })
        assertEquals(listOf(0L, 60L), response.upcomingBirthdayFriends.map { it.birthdayDday })
        assertEquals(2L, response.friends.first { it.userId == in60Days.id }.wishCount)
        assertEquals(null, response.friends.first { it.userId == noBirthday.id }.birthdayDday)
    }

    @Test
    fun `친구를 끊으면 양쪽 관계가 모두 삭제되고 친구가 아니면 예외가 발생한다`() {
        val me = saveUser(kakaoId = 1L)
        val friend = saveUser(kakaoId = 2L)
        befriend(me, friend)

        friendService.deleteFriend(me.id, friend.id)

        assertEquals(0, friendshipRepository.count())
        val exception = assertFailsWith<BusinessException> { friendService.deleteFriend(me.id, friend.id) }
        assertEquals(FriendErrorCode.FRIEND_NOT_FOUND, exception.errorCode)
    }

    @Test
    fun `카카오 친구 중 활성 보탬 회원과 서로 친구가 되고 나 자신과 탈퇴 회원, 이미 친구인 회원은 건너뛴다`() {
        val me = saveUser(kakaoId = 1L)
        val kakaoFriend = saveUser(kakaoId = 2L)
        val alreadyFriend = saveUser(kakaoId = 3L)
        val withdrawn = saveUser(kakaoId = 4L).also { it.withdraw(LocalDateTime.now()) }
        befriend(me, alreadyFriend)
        userRepository.flush()

        // 5는 보탬에 가입하지 않은 카카오 친구
        friendService.connectKakaoFriends(me.id, listOf(1L, 2L, 3L, 4L, 5L))

        val pairs = friendshipRepository.findAll().map { it.userId to it.friendId }.toSet()
        assertEquals(
            setOf(me.id to kakaoFriend.id, kakaoFriend.id to me.id, me.id to alreadyFriend.id, alreadyFriend.id to me.id),
            pairs,
        )
        assertFalse(friendshipRepository.existsByUserIdAndFriendId(me.id, withdrawn.id))
    }

    @Test
    fun `둘 중 누구라도 직접 삭제한 친구는 카카오 친구여도 다시 맺지 않고 초대 링크로는 다시 맺을 수 있다`() {
        val me = saveUser(kakaoId = 1L)
        val removedByMe = saveUser(kakaoId = 2L)
        val removedMe = saveUser(kakaoId = 3L)
        befriend(me, removedByMe)
        befriend(me, removedMe)
        friendService.deleteFriend(me.id, removedByMe.id)
        friendService.deleteFriend(removedMe.id, me.id)

        // 삭제 기록은 삭제한 사람 → 삭제된 사람 방향으로 남는다
        assertEquals(
            setOf(me.id to removedByMe.id, removedMe.id to me.id),
            friendRemovalRepository.findAll().map { it.userId to it.friendId }.toSet(),
        )
        assertTrue(friendRemovalRepository.findAll().all { it.id > 0 })

        friendService.connectKakaoFriends(me.id, listOf(2L, 3L))

        assertEquals(0, friendshipRepository.count())

        // 초대 링크로 다시 친구가 되면 삭제 기록을 지워, 다시 삭제할 때 새로 남길 수 있다
        befriend(me, removedByMe)
        assertTrue(friendshipRepository.existsByUserIdAndFriendId(me.id, removedByMe.id))
        assertFalse(friendRemovalRepository.existsByUserIdAndFriendId(me.id, removedByMe.id))
        friendService.deleteFriend(me.id, removedByMe.id)
        assertTrue(friendRemovalRepository.existsByUserIdAndFriendId(me.id, removedByMe.id))
    }

    @Test
    fun `탈퇴하면 탈퇴 회원의 친구 관계를 모두 삭제한다`() {
        val me = saveUser(kakaoId = 1L)
        val friend = saveUser(kakaoId = 2L)
        val other = saveUser(kakaoId = 3L)
        befriend(me, friend)
        befriend(friend, other)

        friendService.deleteAllFriendships(UserWithdrawnEvent(me.id))

        assertFalse(friendshipRepository.existsByUserIdAndFriendId(friend.id, me.id))
        assertTrue(friendshipRepository.existsByUserIdAndFriendId(friend.id, other.id))
        assertEquals(2, friendshipRepository.count())
    }

    @Test
    fun `탈퇴하면 탈퇴 회원이 남기거나 받은 친구 삭제 기록도 지운다`() {
        val me = saveUser(kakaoId = 1L)
        val friend = saveUser(kakaoId = 2L)
        val other = saveUser(kakaoId = 3L)
        befriend(me, friend)
        befriend(other, me)
        friendService.deleteFriend(me.id, friend.id)
        friendService.deleteFriend(other.id, me.id)

        friendService.deleteAllFriendships(UserWithdrawnEvent(me.id))

        assertEquals(0, friendRemovalRepository.count())
    }

    @Test
    fun `친구 프로필과 같은 기준으로 진행 중 펀딩과 서프라이즈 배지를 붙인다`() {
        val me = saveUser(kakaoId = 1L, name = "나")
        val birthdayFriend = saveUser(kakaoId = 2L, name = "가영")
        val surpriseFriend = saveUser(kakaoId = 3L, name = "나연")
        val hiddenSurpriseFriend = saveUser(kakaoId = 4L, name = "다은")
        val organizerFriend = saveUser(kakaoId = 5L, name = "라희")
        val endedFriend = saveUser(kakaoId = 6L, name = "마루")
        listOf(birthdayFriend, surpriseFriend, hiddenSurpriseFriend, organizerFriend, endedFriend)
            .forEach { befriend(me, it) }
        fundingRepository.saveAll(
            listOf(
                funding(ownerId = birthdayFriend.id, deadline = NOW.plusDays(3)),
                funding(ownerId = me.id, recipientId = surpriseFriend.id, deadline = NOW.plusDays(3)),
                // 받는 사람 프로필에 알리지 않기로 한 서프라이즈는 배지도 숨긴다
                funding(ownerId = me.id, recipientId = hiddenSurpriseFriend.id, deadline = NOW.plusDays(3), showOnRecipientProfile = false),
                // 친구가 나에게 여는 서프라이즈는 나에게 드러나면 안 된다
                funding(ownerId = organizerFriend.id, recipientId = me.id, deadline = NOW.plusDays(3)),
                funding(ownerId = endedFriend.id, deadline = NOW.minusDays(1)),
            ),
        )

        val badges = friendService.getFriends(me.id).friends
            .associate { it.name to (it.fundingInProgress to it.surpriseFundingInProgress) }

        assertEquals(true to false, badges["가영"])
        assertEquals(false to true, badges["나연"])
        assertEquals(false to false, badges["다은"])
        assertEquals(false to false, badges["라희"])
        assertEquals(false to false, badges["마루"])
    }

    @Test
    fun `친구가 없으면 빈 목록을 반환한다`() {
        val me = saveUser(kakaoId = 1L)

        val response = friendService.getFriends(me.id)

        assertEquals(0, response.totalCount)
        assertTrue(response.friends.isEmpty())
    }

    private fun funding(
        ownerId: Long,
        deadline: LocalDateTime,
        recipientId: Long? = null,
        showOnRecipientProfile: Boolean = true,
    ): Funding =
        Funding(
            ownerId = ownerId,
            recipientId = recipientId,
            type = if (recipientId == null) FundingType.BIRTHDAY else FundingType.SURPRISE,
            title = null,
            targetAmount = 100_000L,
            deadline = deadline,
            revealAt = recipientId?.let { deadline.plusDays(1) },
            story = null,
            promise = null,
            product = FundingProductSnapshot(
                sourceWishId = null,
                name = "향수",
                imageUrl = "https://example.com/perfume.jpg",
                price = 100_000L,
                url = "https://example.com/perfume",
            ),
            payoutAccount = FundingPayoutAccountSnapshot(
                sourcePayoutAccountId = null,
                bank = Bank.KAKAO_BANK,
                encryptedAccountNumber = "encrypted",
                accountHolder = null,
            ),
            showOnRecipientProfile = showOnRecipientProfile,
        )

    private fun befriend(user: User, friend: User) {
        friendService.addFriend(user.id, checkNotNull(friend.wishProfileKey))
    }

    private fun saveUser(
        kakaoId: Long,
        name: String? = "회원$kakaoId",
        nickname: String = "닉네임$kakaoId",
        birthday: MonthDay? = null,
    ): User =
        userRepository.save(
            User(kakaoId = kakaoId, nickname = nickname, profileImageUrl = null, name = name, birthday = birthday),
        )

    private fun wish(userId: Long): Wish =
        Wish(
            userId = userId,
            productName = "향수",
            productImageUrl = "https://example.com/perfume.jpg",
            price = 10_000,
            productUrl = "https://example.com/perfume",
        )

    companion object {
        private val NOW = LocalDateTime.of(2026, 11, 12, 12, 0)
    }

    @TestConfiguration
    class FixedClockConfig {
        // 2026-11-12 한국 날짜 기준
        @Bean
        fun clock(): Clock = Clock.fixed(Instant.parse("2026-11-12T03:00:00Z"), ZoneId.of("Asia/Seoul"))
    }
}
