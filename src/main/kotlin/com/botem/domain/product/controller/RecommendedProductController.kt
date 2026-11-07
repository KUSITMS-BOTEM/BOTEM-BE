package com.botem.domain.product.controller

import com.botem.domain.product.controller.docs.RecommendedProductControllerDocs
import com.botem.domain.product.dto.response.RecommendedProductResponse
import com.botem.domain.product.entity.ProductCategory
import com.botem.domain.product.service.RecommendedProductService
import com.botem.global.response.ApiResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/recommended-products")
class RecommendedProductController(
    private val recommendedProductService: RecommendedProductService,
) : RecommendedProductControllerDocs {

    @GetMapping
    override fun getRecommendedProducts(
        @RequestParam(required = false) category: ProductCategory?,
    ): ApiResponse<List<RecommendedProductResponse>> =
        ApiResponse.success(recommendedProductService.getRecommendedProducts(category))

    @GetMapping("/{productId}")
    override fun getRecommendedProduct(
        @PathVariable productId: Long,
    ): ApiResponse<RecommendedProductResponse> =
        ApiResponse.success(recommendedProductService.getRecommendedProduct(productId))
}
