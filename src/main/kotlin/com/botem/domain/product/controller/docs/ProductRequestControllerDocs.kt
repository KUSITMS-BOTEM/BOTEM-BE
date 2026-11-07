package com.botem.domain.product.controller.docs

import com.botem.domain.product.dto.request.RequestProductRequest
import com.botem.global.response.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.parameters.RequestBody
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Product Request", description = "상품 추가 요청 API")
interface ProductRequestControllerDocs {

    @Operation(
        summary = "쿠팡 상품 추가 요청",
        description = """
            찾는 선물이 추천 목록에 없을 때 쿠팡 상품 링크로 추가를 요청합니다.
            링크는 https:// 형식과 쿠팡 도메인(coupang.com, www.coupang.com, m.coupang.com, link.coupang.com, coupa.ng)만 확인하며,
            상품 정보는 불러오지 않습니다. 상품 이름은 선택입니다.
            요청 처리 결과는 따로 알리지 않습니다.
        """,
    )
    @RequestBody(
        content = [
            Content(
                examples = [
                    ExampleObject(
                        value = """{ "productUrl": "https://www.coupang.com/vp/products/123456", "productName": "다이슨 에어랩 i.d." }""",
                    ),
                ],
            ),
        ],
    )
    @ApiResponses(
        SwaggerApiResponse(
            responseCode = "201",
            description = "요청 성공",
            content = [Content(examples = [ExampleObject(value = SUCCESS_EXAMPLE)])],
        ),
        SwaggerApiResponse(
            responseCode = "400",
            description = "링크 형식 오류 또는 쿠팡 외 링크",
            content = [
                Content(
                    examples = [
                        ExampleObject(name = "PRODUCT_003", summary = "링크 형식 오류", value = INVALID_URL_EXAMPLE),
                        ExampleObject(name = "PRODUCT_004", summary = "쿠팡 외 링크", value = NOT_COUPANG_URL_EXAMPLE),
                    ],
                ),
            ],
        ),
        SwaggerApiResponse(responseCode = "401", description = "인증 실패"),
    )
    fun requestProduct(
        @Parameter(hidden = true) userId: Long,
        request: RequestProductRequest,
    ): ApiResponse<Nothing>

    companion object {
        private const val SUCCESS_EXAMPLE =
            """{ "success": true, "data": null, "error": null }"""

        private const val INVALID_URL_EXAMPLE = """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "PRODUCT_003",
                "message": "https://로 시작하는 링크를 붙여넣어 주세요.",
                "detail": null
              }
            }
        """

        private const val NOT_COUPANG_URL_EXAMPLE = """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "PRODUCT_004",
                "message": "쿠팡 상품 링크만 보낼 수 있어요.",
                "detail": null
              }
            }
        """
    }
}
