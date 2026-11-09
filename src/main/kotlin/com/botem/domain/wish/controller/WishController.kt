package com.botem.domain.wish.controller

import com.botem.domain.wish.controller.docs.WishControllerDocs
import com.botem.domain.wish.dto.request.AddWishRequest
import com.botem.domain.wish.dto.response.WishPageResponse
import com.botem.domain.wish.dto.response.WishResponse
import com.botem.domain.wish.service.WishService
import com.botem.global.response.ApiResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/wishes")
class WishController(
    private val wishService: WishService,
) : WishControllerDocs {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    override fun addWish(
        @AuthenticationPrincipal userId: Long,
        @Valid @RequestBody request: AddWishRequest,
    ): ApiResponse<WishResponse> =
        ApiResponse.success(wishService.addRecommendedProduct(userId, request.recommendedProductId))

    @GetMapping
    override fun getWishes(
        @AuthenticationPrincipal userId: Long,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int,
    ): ApiResponse<WishPageResponse> =
        ApiResponse.success(wishService.getWishes(userId, page, size))

    @GetMapping("/{wishId}")
    override fun getWish(
        @AuthenticationPrincipal userId: Long,
        @PathVariable wishId: Long,
    ): ApiResponse<WishResponse> =
        ApiResponse.success(wishService.getWish(userId, wishId))

    @DeleteMapping("/{wishId}")
    override fun deleteWish(
        @AuthenticationPrincipal userId: Long,
        @PathVariable wishId: Long,
    ): ApiResponse<Nothing> {
        wishService.deleteWish(userId, wishId)

        return ApiResponse.success()
    }
}
