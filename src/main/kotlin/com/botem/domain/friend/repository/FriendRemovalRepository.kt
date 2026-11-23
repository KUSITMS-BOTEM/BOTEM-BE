package com.botem.domain.friend.repository

import com.botem.domain.friend.entity.FriendRemoval
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface FriendRemovalRepository : JpaRepository<FriendRemoval, Long> {

    fun existsByUserIdAndFriendId(userId: Long, friendId: Long): Boolean

    // 후보 중 나와 둘 중 누구라도 삭제한 기록이 있는 상대 회원 ID
    @Query(
        """
        SELECT CASE WHEN removal.userId = :userId THEN removal.friendId ELSE removal.userId END
        FROM FriendRemoval removal
        WHERE (removal.userId = :userId AND removal.friendId IN :candidateIds)
           OR (removal.friendId = :userId AND removal.userId IN :candidateIds)
        """,
    )
    fun findRemovedCounterpartIds(
        @Param("userId") userId: Long,
        @Param("candidateIds") candidateIds: Collection<Long>,
    ): List<Long>

    // 초대 링크로 다시 친구가 되면 삭제 기록을 지워, 이후 다시 삭제할 때 새로 남길 수 있게 한다
    @Modifying(flushAutomatically = true)
    @Query(
        """
        DELETE FROM FriendRemoval removal
        WHERE (removal.userId = :userId AND removal.friendId = :friendId)
           OR (removal.userId = :friendId AND removal.friendId = :userId)
        """,
    )
    fun deleteBetween(@Param("userId") userId: Long, @Param("friendId") friendId: Long): Int

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM FriendRemoval removal WHERE removal.userId = :userId")
    fun deleteAllByUserId(@Param("userId") userId: Long): Int

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM FriendRemoval removal WHERE removal.friendId = :friendId")
    fun deleteAllByFriendId(@Param("friendId") friendId: Long): Int
}
