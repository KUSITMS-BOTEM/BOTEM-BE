package com.botem.domain.wish.controller.docs

import com.botem.domain.wish.dto.request.AddWishRequest
import com.botem.domain.wish.dto.response.WishPageResponse
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

    @Operation(
        summary = "내 위시 목록 조회",
        description = """
            위시 탭의 내 위시를 최근에 담은 순서로 조회합니다. size는 1~20, 기본 10입니다.
            status: AVAILABLE(펀딩 열기 가능), FUNDING(내가 연 펀딩이 끝나지 않음, fundingId 포함),
            DISCONTINUED(추천 상품 판매 종료). FUNDING이 DISCONTINUED보다 우선합니다.
            닉네임·생일 등 상단 정보는 내 정보 조회 API를 사용합니다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = [Content(examples = [ExampleObject(value = WISH_PAGE_RESPONSE_EXAMPLE)])],
        ),
        SwaggerApiResponse(responseCode = "400", description = "page 또는 size 범위 오류"),
        SwaggerApiResponse(responseCode = "401", description = "인증 실패"),
    )
    fun getWishes(
        @Parameter(hidden = true) userId: Long,
        @Parameter(description = "페이지 번호 (0부터)") page: Int,
        @Parameter(description = "페이지 크기 (1~20)") size: Int,
    ): ApiResponse<WishPageResponse>

    @Operation(
        summary = "위시 상세 조회",
        description = """
            내 위시 하나를 조회합니다. status가 FUNDING이면 "펀딩 보기" 버튼으로 fundingId의 펀딩으로 이동합니다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = [Content(examples = [ExampleObject(value = WISH_RESPONSE_EXAMPLE)])],
        ),
        SwaggerApiResponse(responseCode = "401", description = "인증 실패"),
        SwaggerApiResponse(
            responseCode = "404",
            description = "위시 없음 (다른 회원의 위시 포함)",
            content = [Content(examples = [ExampleObject(value = WISH_NOT_FOUND_EXAMPLE)])],
        ),
    )
    fun getWish(
        @Parameter(hidden = true) userId: Long,
        @Parameter(description = "위시 ID") wishId: Long,
    ): ApiResponse<WishResponse>

    @Operation(
        summary = "위시 삭제",
        description = """
            내 위시를 삭제합니다. 내가 연 펀딩이 끝나지 않은(완료·취소 외) 위시는 삭제할 수 없습니다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(
            responseCode = "200",
            description = "삭제 성공",
            content = [Content(examples = [ExampleObject(value = SUCCESS_EXAMPLE)])],
        ),
        SwaggerApiResponse(responseCode = "401", description = "인증 실패"),
        SwaggerApiResponse(
            responseCode = "404",
            description = "위시 없음 (다른 회원의 위시 포함)",
            content = [Content(examples = [ExampleObject(value = WISH_NOT_FOUND_EXAMPLE)])],
        ),
        SwaggerApiResponse(
            responseCode = "409",
            description = "펀딩 중인 위시",
            content = [Content(examples = [ExampleObject(value = WISH_IN_FUNDING_EXAMPLE)])],
        ),
    )
    fun deleteWish(
        @Parameter(hidden = true) userId: Long,
        @Parameter(description = "위시 ID") wishId: Long,
    ): ApiResponse<Nothing>

    companion object {
        private const val SUCCESS_EXAMPLE =
            """{ "success": true, "data": null, "error": null }"""

        private const val WISH_PAGE_RESPONSE_EXAMPLE = """
            {
              "success": true,
              "data": {
                "content": [
                  {
                    "id": 2,
                    "productName": "소니 WH-1000XM5",
                    "productImageUrl": "https://example.com/sony.jpg",
                    "price": 429000,
                    "productUrl": "https://link.coupang.com/a/sony",
                    "status": "FUNDING",
                    "fundingId": 7
                  },
                  {
                    "id": 1,
                    "productName": "에어팟 프로 3",
                    "productImageUrl": "https://example.com/airpods.jpg",
                    "price": 350000,
                    "productUrl": "https://link.coupang.com/a/example",
                    "status": "DISCONTINUED",
                    "fundingId": null
                  }
                ],
                "page": 0,
                "size": 10,
                "totalElements": 2,
                "hasNext": false
              },
              "error": null
            }
        """

        private const val WISH_NOT_FOUND_EXAMPLE = """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "WISH_002",
                "message": "위시를 찾을 수 없습니다.",
                "detail": null
              }
            }
        """

        private const val WISH_IN_FUNDING_EXAMPLE = """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "WISH_003",
                "message": "펀딩 중인 위시는 삭제할 수 없습니다.",
                "detail": null
              }
            }
        """
        private const val WISH_RESPONSE_EXAMPLE = """
            {
              "success": true,
              "data": {
                "id": 1,
                "productName": "에어팟 프로 3",
                "productImageUrl": "https://example.com/airpods.jpg",
                "price": 350000,
                "productUrl": "https://link.coupang.com/a/example",
                "status": "AVAILABLE",
                "fundingId": null
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
