package com.botem.domain.funding.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(
    name = "funding_product_change_histories",
    indexes = [
        Index(name = "idx_funding_product_changes_funding_changed", columnList = "funding_id, changed_at"),
    ],
)
class FundingProductChangeHistory private constructor(
    @Column(nullable = false, updatable = false)
    val fundingId: Long,

    @Column(updatable = false)
    val previousSourceWishId: Long?,

    @Column(nullable = false, updatable = false, length = 100)
    val previousProductName: String,

    @Column(nullable = false, updatable = false, length = 2048)
    val previousProductImageUrl: String,

    @Column(nullable = false, updatable = false)
    val previousProductPrice: Long,

    @Column(nullable = false, updatable = false, length = 2048)
    val previousProductUrl: String,

    @Column(updatable = false)
    val changedSourceWishId: Long?,

    @Column(nullable = false, updatable = false, length = 100)
    val changedProductName: String,

    @Column(nullable = false, updatable = false, length = 2048)
    val changedProductImageUrl: String,

    @Column(nullable = false, updatable = false)
    val changedProductPrice: Long,

    @Column(nullable = false, updatable = false, length = 2048)
    val changedProductUrl: String,

    @Column(nullable = false, updatable = false)
    val changedAt: LocalDateTime,
) {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    companion object {
        fun record(
            fundingId: Long,
            previousProduct: FundingProductSnapshot,
            changedProduct: FundingProductSnapshot,
            changedAt: LocalDateTime,
        ): FundingProductChangeHistory =
            FundingProductChangeHistory(
                fundingId = fundingId,
                previousSourceWishId = previousProduct.sourceWishId,
                previousProductName = previousProduct.name,
                previousProductImageUrl = previousProduct.imageUrl,
                previousProductPrice = previousProduct.price,
                previousProductUrl = previousProduct.url,
                changedSourceWishId = changedProduct.sourceWishId,
                changedProductName = changedProduct.name,
                changedProductImageUrl = changedProduct.imageUrl,
                changedProductPrice = changedProduct.price,
                changedProductUrl = changedProduct.url,
                changedAt = changedAt,
            )
    }
}
