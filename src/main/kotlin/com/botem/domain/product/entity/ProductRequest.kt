package com.botem.domain.product.entity

import com.botem.global.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table

// 추천 목록에 없는 쿠팡 상품의 추가 요청. 운영자가 요청 수를 집계해 추천 상품 등록 우선순위를 정함
@Entity
@Table(
    name = "product_requests",
    indexes = [
        Index(name = "idx_product_requests_coupang_product_id", columnList = "coupang_product_id"),
    ],
)
class ProductRequest(
    @Column(nullable = false, updatable = false)
    val userId: Long,

    @Column(nullable = false, updatable = false, length = 2048)
    val productUrl: String,

    @Column(updatable = false, length = 20)
    val coupangProductId: String?,

    @Column(updatable = false, length = 100)
    val productName: String?,
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0
}
