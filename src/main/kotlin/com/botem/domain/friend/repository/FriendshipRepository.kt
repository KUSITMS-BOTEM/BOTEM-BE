package com.botem.domain.friend.repository

import com.botem.domain.friend.entity.Friendship
import com.botem.domain.friend.repository.projection.FriendProjection
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface FriendshipRepository : JpaRepository<Friendship, Long> {

    fun existsByUserIdAndFriendId(userId: Long, friendId: Long): Boolean

    @Query(
        """
        SELECT new com.botem.domain.friend.repository.projection.FriendProjection(
            friend.id,
            friend.name,
            friend.nickname,
            friend.profileImageUrl,
            friend.birthday,
            COUNT(wish.id)
        )
        FROM Friendship friendship
        JOIN User friend ON friend.id = friendship.friendId
        LEFT JOIN Wish wish ON wish.userId = friend.id
        WHERE friendship.userId = :userId
          AND friend.status = com.botem.domain.user.entity.UserStatus.ACTIVE
        GROUP BY friend.id, friend.name, friend.nickname, friend.profileImageUrl, friend.birthday
        """,
    )
    fun findFriends(@Param("userId") userId: Long): List<FriendProjection>

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        """
        DELETE FROM Friendship friendship
        WHERE (friendship.userId = :userId AND friendship.friendId = :friendId)
           OR (friendship.userId = :friendId AND friendship.friendId = :userId)
        """,
    )
    fun deleteBetween(@Param("userId") userId: Long, @Param("friendId") friendId: Long): Int

    // 탈퇴 트랜잭션 안에서 실행되므로 영속성 컨텍스트를 비우지 않는다.
    // OR 조건은 전체 스캔으로 테이블 전체를 잠글 수 있어 컬럼별 인덱스를 타도록 두 쿼리로 나눈다
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM Friendship friendship WHERE friendship.userId = :userId")
    fun deleteAllByUserId(@Param("userId") userId: Long): Int

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM Friendship friendship WHERE friendship.friendId = :friendId")
    fun deleteAllByFriendId(@Param("friendId") friendId: Long): Int
}
