package com.botem.domain.wish.controller

import com.botem.domain.wish.controller.docs.WishControllerDocs
import com.botem.domain.wish.dto.request.AddWishRequest
import com.botem.domain.wish.dto.response.WishResponse
import com.botem.domain.wish.service.WishService
import com.botem.global.response.ApiResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
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
}
