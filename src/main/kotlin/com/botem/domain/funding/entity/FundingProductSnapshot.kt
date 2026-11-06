package com.botem.domain.funding.entity

import jakarta.persistence.Column
import jakarta.persistence.Embeddable

@Embeddable
class FundingProductSnapshot(
    @Column(name = "source_wish_id")
    val sourceWishId: Long?,

    @Column(name = "product_name", nullable = false, length = 20)
    val name: String,

    @Column(name = "product_image_url", nullable = false, length = 2048)
    val imageUrl: String,

    @Column(name = "product_price", nullable = false)
    val price: Long,

    @Column(name = "product_url", nullable = false, length = 2048)
    val url: String,
)
