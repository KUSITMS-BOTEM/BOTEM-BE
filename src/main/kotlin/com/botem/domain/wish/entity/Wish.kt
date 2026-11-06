package com.botem.domain.wish.entity

import com.botem.global.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table

@Entity
@Table(
    name = "wishes",
    indexes = [
        Index(name = "idx_wishes_user_id_created_at", columnList = "user_id, created_at"),
    ],
)
class Wish(
    @Column(nullable = false, updatable = false)
    val userId: Long,

    productName: String,

    productImageUrl: String,

    price: Long,

    productUrl: String,
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @Column(nullable = false, length = 20)
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
