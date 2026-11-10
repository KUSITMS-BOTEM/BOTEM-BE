package com.botem.domain.product.controller.docs

import com.botem.domain.product.dto.request.CreateRecommendedProductRequest
import com.botem.domain.product.dto.response.RecommendedProductResponse
import com.botem.global.config.SwaggerConfig
import com.botem.global.response.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.parameters.RequestBody
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Admin Recommended Product", description = "운영자 추천 상품 관리 API")
@SecurityRequirement(name = SwaggerConfig.ADMIN_API_KEY_AUTH)
interface AdminRecommendedProductControllerDocs {

    @Operation(
        summary = "추천 상품 등록",
        description = """
            운영자가 추천 상품을 등록합니다. X-Admin-Key 헤더에 운영자 키가 필요합니다.
            등록된 상품은 판매 중 상태로 추천 목록에 바로 노출됩니다.
            displayOrder는 생략하면 0이며, 작을수록 먼저 노출됩니다.
        """,
    )
    @RequestBody(
        content = [
            Content(
                examples = [
                    ExampleObject(
                        value = """
                            {
                              "name": "소니 WH-1000XM5",
                              "imageUrl": "https://image.coupangcdn.com/example.jpg",
                              "price": 429000,
                              "sellerName": "쿠팡 Apple 공식 브랜드관",
                              "productUrl": "https://link.coupang.com/a/example",
                              "category": "EARPHONE_HEADPHONE",
                              "displayOrder": 0
                            }
                        """,
                    ),
                ],
            ),
        ],
    )
    @ApiResponses(
        SwaggerApiResponse(
            responseCode = "201",
            description = "등록 성공",
            content = [Content(examples = [ExampleObject(value = SUCCESS_EXAMPLE)])],
        ),
        SwaggerApiResponse(responseCode = "400", description = "요청 값 오류"),
        SwaggerApiResponse(responseCode = "401", description = "운영자 키 없음 또는 불일치"),
    )
    fun createRecommendedProduct(
        request: CreateRecommendedProductRequest,
    ): ApiResponse<RecommendedProductResponse>

    companion object {
        private const val SUCCESS_EXAMPLE = """
            {
              "success": true,
              "data": {
                "id": 1,
                "name": "소니 WH-1000XM5",
                "imageUrl": "https://image.coupangcdn.com/example.jpg",
                "price": 429000,
                "sellerName": "쿠팡 Apple 공식 브랜드관",
                "category": "EARPHONE_HEADPHONE",
                "status": "ON_SALE"
              },
              "error": null
            }
        """
    }
}
