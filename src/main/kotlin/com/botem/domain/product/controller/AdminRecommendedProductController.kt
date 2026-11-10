package com.botem.domain.product.controller

import com.botem.domain.product.controller.docs.AdminRecommendedProductControllerDocs
import com.botem.domain.product.dto.request.CreateRecommendedProductRequest
import com.botem.domain.product.dto.response.RecommendedProductResponse
import com.botem.domain.product.service.RecommendedProductService
import com.botem.global.response.ApiResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin/recommended-products")
class AdminRecommendedProductController(
    private val recommendedProductService: RecommendedProductService,
) : AdminRecommendedProductControllerDocs {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    override fun createRecommendedProduct(
        @Valid @RequestBody request: CreateRecommendedProductRequest,
    ): ApiResponse<RecommendedProductResponse> =
        ApiResponse.success(recommendedProductService.createRecommendedProduct(request))
}
