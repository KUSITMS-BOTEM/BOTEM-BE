package com.botem.domain.product.controller.docs

import com.botem.domain.product.dto.response.RecommendedProductResponse
import com.botem.domain.product.entity.ProductCategory
import com.botem.global.response.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Recommended Product", description = "추천 선물 API")
interface RecommendedProductControllerDocs {

    @Operation(
        summary = "추천 선물 목록 조회",
        description = """
            위시 추가 화면의 추천 선물 카드를 노출 순서대로 조회합니다.
            category를 생략하면 전체 카테고리를 조회하며, 판매 종료된 상품은 제외됩니다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = [Content(examples = [ExampleObject(value = LIST_RESPONSE_EXAMPLE)])],
        ),
        SwaggerApiResponse(
            responseCode = "400",
            description = "잘못된 카테고리",
            content = [Content(examples = [ExampleObject(value = INVALID_CATEGORY_EXAMPLE)])],
        ),
        SwaggerApiResponse(responseCode = "401", description = "인증 실패"),
    )
    fun getRecommendedProducts(
        @Parameter(description = "카테고리 (생략 시 전체)") category: ProductCategory?,
    ): ApiResponse<List<RecommendedProductResponse>>

    @Operation(
        summary = "추천 선물 상세 조회",
        description = """
            추천 선물 하나를 조회합니다.
            판매 종료된 상품도 조회되며, status가 DISCONTINUED이면 "지금은 고를 수 없는 선물" 화면을 표시합니다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = [Content(examples = [ExampleObject(value = DETAIL_RESPONSE_EXAMPLE)])],
        ),
        SwaggerApiResponse(responseCode = "401", description = "인증 실패"),
        SwaggerApiResponse(
            responseCode = "404",
            description = "추천 상품 없음",
            content = [Content(examples = [ExampleObject(value = NOT_FOUND_EXAMPLE)])],
        ),
    )
    fun getRecommendedProduct(
        @Parameter(description = "추천 상품 ID") productId: Long,
    ): ApiResponse<RecommendedProductResponse>

    companion object {
        private const val LIST_RESPONSE_EXAMPLE = """
            {
              "success": true,
              "data": [
                {
                  "id": 1,
                  "name": "에어팟 프로 3",
                  "imageUrl": "https://example.com/airpods.jpg",
                  "price": 350000,
                  "sellerName": "Apple 공식 브랜드관",
                  "category": "EARPHONE_HEADPHONE",
                  "status": "ON_SALE"
                }
              ],
              "error": null
            }
        """

        private const val DETAIL_RESPONSE_EXAMPLE = """
            {
              "success": true,
              "data": {
                "id": 1,
                "name": "에어팟 프로 3",
                "imageUrl": "https://example.com/airpods.jpg",
                "price": 350000,
                "sellerName": "Apple 공식 브랜드관",
                "category": "EARPHONE_HEADPHONE",
                "status": "ON_SALE"
              },
              "error": null
            }
        """

        private const val INVALID_CATEGORY_EXAMPLE = """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "COMMON_001",
                "message": "입력값이 올바르지 않습니다.",
                "detail": "category: 올바르지 않은 값입니다"
              }
            }
        """

        private const val NOT_FOUND_EXAMPLE = """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "PRODUCT_001",
                "message": "추천 상품을 찾을 수 없습니다.",
                "detail": null
              }
            }
        """
    }
}
