package com.botem.domain.wish.entity

import com.botem.global.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "wishes",
    indexes = [
        Index(name = "idx_wishes_user_id_created_at", columnList = "user_id, created_at"),
    ],
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_wishes_user_recommended_product",
            columnNames = ["user_id", "recommended_product_id"],
        ),
    ],
)
class Wish(
    @Column(nullable = false, updatable = false)
    val userId: Long,

    // 담은 시점의 상품 정보를 그대로 보관한다 (수정 기능 없음)
    @Column(nullable = false, length = 100)
    val productName: String,

    @Column(nullable = false, length = 2048)
    val productImageUrl: String,

    @Column(nullable = false)
    val price: Long,

    @Column(nullable = false, length = 2048)
    val productUrl: String,

    // 추천 상품이 아닌 기존 위시는 판매처를 알 수 없어 null
    @Column(length = 100, updatable = false)
    val sellerName: String? = null,

    // 추천 상품에서 담은 위시면 원본 상품을 가리켜, 운영자가 판매 종료하면 위시에도 반영
    @Column(updatable = false)
    val recommendedProductId: Long? = null,
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0
}
