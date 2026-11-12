package com.botem.domain.friend.service

import com.botem.domain.friend.exception.FriendErrorCode
import com.botem.domain.friend.repository.FriendshipRepository
import com.botem.domain.user.entity.User
import com.botem.domain.user.repository.UserRepository
import com.botem.domain.user.service.UserWithdrawnEvent
import com.botem.domain.wish.entity.Wish
import com.botem.domain.wish.repository.WishRepository
import com.botem.global.config.JpaAuditingConfig
import com.botem.global.exception.BusinessException
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.MonthDay
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@DataJpaTest
@Import(JpaAuditingConfig::class, FriendService::class, FriendServiceTest.FixedClockConfig::class)
class FriendServiceTest {

    @Autowired
    private lateinit var friendService: FriendService

    @Autowired
    private lateinit var friendshipRepository: FriendshipRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var wishRepository: WishRepository

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

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

    @TestConfiguration
    class FixedClockConfig {
        // 2026-11-12 한국 날짜 기준
        @Bean
        fun clock(): Clock = Clock.fixed(Instant.parse("2026-11-12T03:00:00Z"), ZoneId.of("Asia/Seoul"))
    }
}
