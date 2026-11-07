package com.botem.domain.product.controller

import com.botem.domain.product.controller.docs.ProductRequestControllerDocs
import com.botem.domain.product.dto.request.RequestProductRequest
import com.botem.domain.product.service.ProductRequestService
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
@RequestMapping("/api/product-requests")
class ProductRequestController(
    private val productRequestService: ProductRequestService,
) : ProductRequestControllerDocs {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    override fun requestProduct(
        @AuthenticationPrincipal userId: Long,
        @Valid @RequestBody request: RequestProductRequest,
    ): ApiResponse<Nothing> {
        productRequestService.requestProduct(userId, request)

        return ApiResponse.success()
    }
}
