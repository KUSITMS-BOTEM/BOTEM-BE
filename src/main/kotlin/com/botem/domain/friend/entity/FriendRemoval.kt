package com.botem.domain.friend.entity

import com.botem.global.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

// 사용자(userId)가 직접 삭제한 친구(friendId). 카카오 친구 자동 연결은 두 사람 중 한쪽이라도 삭제했으면 다시 맺지 않는다
@Entity
@Table(
    name = "friend_removals",
    indexes = [
        Index(name = "idx_friend_removals_friend_id", columnList = "friend_id"),
    ],
    uniqueConstraints = [
        UniqueConstraint(name = "uk_friend_removals_user_friend", columnNames = ["user_id", "friend_id"]),
    ],
)
class FriendRemoval(
    @Column(nullable = false, updatable = false)
    val userId: Long,

    @Column(nullable = false, updatable = false)
    val friendId: Long,
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0
}
