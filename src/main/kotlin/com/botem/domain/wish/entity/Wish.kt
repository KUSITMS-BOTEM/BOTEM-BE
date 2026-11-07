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

    productName: String,

    productImageUrl: String,

    price: Long,

    productUrl: String,

    // 추천 상품에서 담은 위시면 원본 상품을 가리켜, 운영자가 판매 종료하면 위시에도 반영
    @Column(updatable = false)
    val recommendedProductId: Long? = null,
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @Column(nullable = false, length = 100)
    var productName: String = productName
        protected set

    @Column(nullable = false, length = 2048)
    var productImageUrl: String = productImageUrl
        protected set

    @Column(nullable = false)
    var price: Long = price
        protected set

    @Column(nullable = false, length = 2048)
    var productUrl: String = productUrl
        protected set

    fun update(
        productName: String,
        productImageUrl: String,
        price: Long,
        productUrl: String,
    ) {
        this.productName = productName
        this.productImageUrl = productImageUrl
        this.price = price
        this.productUrl = productUrl
    }
}
