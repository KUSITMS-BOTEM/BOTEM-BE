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

// 친구 관계는 양방향 두 행(나→친구, 친구→나)으로 저장한다
@Entity
@Table(
    name = "friendships",
    indexes = [
        Index(name = "idx_friendships_friend_id", columnList = "friend_id"),
    ],
    uniqueConstraints = [
        UniqueConstraint(name = "uk_friendships_user_friend", columnNames = ["user_id", "friend_id"]),
    ],
)
class Friendship(
    @Column(nullable = false, updatable = false)
    val userId: Long,

    @Column(nullable = false, updatable = false)
    val friendId: Long,
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0
}
