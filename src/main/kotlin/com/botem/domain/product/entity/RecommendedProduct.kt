package com.botem.domain.product.entity

import com.botem.global.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table

// 운영자가 고른 추천 상품. 운영자 API로 등록하고, 수정 방식이 정해지기 전까지 수정은 DB에서 직접 관리
@Entity
@Table(
    name = "recommended_products",
    indexes = [
        Index(
            name = "idx_recommended_products_status_category_order",
            columnList = "status, category, display_order",
        ),
    ],
)
class RecommendedProduct(
    @Column(nullable = false, length = 100)
    val name: String,

    @Column(nullable = false, length = 2048)
    val imageUrl: String,

    @Column(nullable = false)
    val price: Long,

    @Column(nullable = false, length = 100)
    val sellerName: String,

    @Column(nullable = false, length = 2048)
    val productUrl: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    val category: ProductCategory,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val status: RecommendedProductStatus = RecommendedProductStatus.ON_SALE,

    @Column(nullable = false)
    val displayOrder: Int = 0,
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0
}
