package com.botem.domain.friend.service

import com.botem.domain.friend.dto.response.FriendListResponse
import com.botem.domain.friend.dto.response.FriendResponse
import com.botem.domain.friend.entity.Friendship
import com.botem.domain.friend.exception.FriendErrorCode
import com.botem.domain.friend.repository.FriendshipRepository
import com.botem.domain.user.entity.UserStatus
import com.botem.domain.user.repository.UserRepository
import com.botem.domain.user.service.UserWithdrawnEvent
import com.botem.global.exception.BusinessException
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.text.Collator
import java.time.Clock
import java.time.LocalDate
import java.util.Locale

@Service
class FriendService(
    private val friendshipRepository: FriendshipRepository,
    private val userRepository: UserRepository,
    private val clock: Clock,
) {

    // 친구 요청·수락 없이 친구의 위시 프로필(초대 링크)에서 로그인하면 서로 친구가 된다
    @Transactional
    fun addFriend(userId: Long, wishProfileKey: String) {
        val friend = userRepository.findByWishProfileKey(wishProfileKey)
            ?.takeIf { it.status == UserStatus.ACTIVE }
            ?: throw BusinessException(FriendErrorCode.FRIEND_TARGET_NOT_FOUND)

        if (friend.id == userId) {
            return
        }

        // 두 회원 행을 id 순서로 잠가 같은 쌍의 동시 요청을 직렬화하고 교착을 피한다
        listOf(userId, friend.id).sorted().forEach { userRepository.findByIdForUpdate(it) }

        if (friendshipRepository.existsByUserIdAndFriendId(userId, friend.id)) {
            return
        }

        friendshipRepository.saveAll(listOf(Friendship(userId, friend.id), Friendship(friend.id, userId)))
    }

    // ponytail: 페이징 없이 전체 조회. 친구 수가 수백 명 단위로 늘면 전체 목록에 페이징 추가
    @Transactional(readOnly = true)
    fun getFriends(userId: Long): FriendListResponse {
        val today = LocalDate.now(clock)
        val koreanOrder = Collator.getInstance(Locale.KOREAN)
        val friends = friendshipRepository.findFriends(userId)
            .map { FriendResponse.from(it, today) }
            .sortedWith(compareBy(koreanOrder) { it.name })

        val upcomingBirthdayFriends = friends
            .filter { friend -> friend.birthdayDday?.let { it <= UPCOMING_BIRTHDAY_DAYS } == true }
            .sortedBy { it.birthdayDday }

        return FriendListResponse(
            totalCount = friends.size,
            upcomingBirthdayFriends = upcomingBirthdayFriends,
            friends = friends,
        )
    }

    @Transactional
    fun deleteFriend(userId: Long, friendId: Long) {
        if (friendshipRepository.deleteBetween(userId, friendId) == 0) {
            throw BusinessException(FriendErrorCode.FRIEND_NOT_FOUND)
        }
    }

    // 탈퇴 트랜잭션 안에서 실행해 탈퇴와 친구 관계 삭제가 함께 커밋·롤백되게 한다
    @EventListener
    fun deleteAllFriendships(event: UserWithdrawnEvent) {
        friendshipRepository.deleteAllByUserId(event.userId)
        friendshipRepository.deleteAllByFriendId(event.userId)
    }

    companion object {
        private const val UPCOMING_BIRTHDAY_DAYS = 60L
    }
}
