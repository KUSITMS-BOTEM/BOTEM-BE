package com.botem.domain.product.dto.request

import com.botem.domain.product.entity.ProductCategory
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size

data class CreateRecommendedProductRequest(
    @field:NotBlank
    @field:Size(max = 100)
    val name: String,

    @field:NotBlank
    @field:Size(max = 2048)
    val imageUrl: String,

    @field:Positive
    val price: Long,

    @field:NotBlank
    @field:Size(max = 100)
    val sellerName: String,

    @field:NotBlank
    @field:Size(max = 2048)
    val productUrl: String,

    val category: ProductCategory,

    @field:PositiveOrZero
    val displayOrder: Int = 0,
)
