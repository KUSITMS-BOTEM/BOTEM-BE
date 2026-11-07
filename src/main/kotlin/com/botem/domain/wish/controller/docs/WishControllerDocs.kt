package com.botem.domain.wish.controller.docs

import com.botem.domain.wish.dto.request.AddWishRequest
import com.botem.domain.wish.dto.response.WishResponse
import com.botem.global.response.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.parameters.RequestBody
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Wish", description = "위시 API")
interface WishControllerDocs {

    @Operation(
        summary = "추천 선물 위시 담기",
        description = """
            추천 선물을 내 위시에 담습니다. 담은 시점의 상품 정보가 위시에 저장됩니다.
            판매 종료된 상품이나 이미 담은 상품은 담을 수 없습니다.
        """,
    )
    @RequestBody(
        content = [Content(examples = [ExampleObject(value = """{ "recommendedProductId": 1 }""")])],
    )
    @ApiResponses(
        SwaggerApiResponse(
            responseCode = "201",
            description = "담기 성공",
            content = [Content(examples = [ExampleObject(value = WISH_RESPONSE_EXAMPLE)])],
        ),
        SwaggerApiResponse(responseCode = "400", description = "요청 값 오류"),
        SwaggerApiResponse(responseCode = "401", description = "인증 실패"),
        SwaggerApiResponse(
            responseCode = "404",
            description = "추천 상품 없음",
            content = [Content(examples = [ExampleObject(value = PRODUCT_NOT_FOUND_EXAMPLE)])],
        ),
        SwaggerApiResponse(
            responseCode = "409",
            description = "판매 종료 상품 또는 이미 담은 상품",
            content = [
                Content(
                    examples = [
                        ExampleObject(name = "PRODUCT_002", summary = "판매 종료 상품", value = DISCONTINUED_EXAMPLE),
                        ExampleObject(name = "WISH_001", summary = "이미 담은 상품", value = ALREADY_ADDED_EXAMPLE),
                    ],
                ),
            ],
        ),
    )
    fun addWish(
        @Parameter(hidden = true) userId: Long,
        request: AddWishRequest,
    ): ApiResponse<WishResponse>

    companion object {
        private const val WISH_RESPONSE_EXAMPLE = """
            {
              "success": true,
              "data": {
                "id": 1,
                "productName": "에어팟 프로 3",
                "productImageUrl": "https://example.com/airpods.jpg",
                "price": 350000,
                "productUrl": "https://link.coupang.com/a/example"
              },
              "error": null
            }
        """

        private const val PRODUCT_NOT_FOUND_EXAMPLE = """
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

        private const val DISCONTINUED_EXAMPLE = """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "PRODUCT_002",
                "message": "판매가 종료된 상품입니다.",
                "detail": null
              }
            }
        """

        private const val ALREADY_ADDED_EXAMPLE = """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "WISH_001",
                "message": "이미 위시에 담은 상품입니다.",
                "detail": null
              }
            }
        """
    }
}
