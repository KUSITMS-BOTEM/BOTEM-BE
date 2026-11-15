package com.botem.domain.funding.controller

import com.botem.domain.funding.dto.request.SendSurpriseThankCardRequest
import com.botem.domain.funding.dto.response.SurpriseRevealDetailResponse
import com.botem.domain.funding.dto.response.SurpriseThankCardResponse
import com.botem.domain.funding.service.SurpriseRevealService
import com.botem.global.response.ApiResponse
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/fundings")
class SurpriseRevealController(
    private val surpriseRevealService: SurpriseRevealService,
) {

    @GetMapping("/{fundingId}/surprise")
    fun getSurpriseRevealDetail(
        @AuthenticationPrincipal userId: Long,
        @PathVariable fundingId: Long,
    ): ApiResponse<SurpriseRevealDetailResponse> =
        ApiResponse.success(surpriseRevealService.getDetail(userId, fundingId))

    @PostMapping("/{fundingId}/surprise/open")
    fun openSurprise(
        @AuthenticationPrincipal userId: Long,
        @PathVariable fundingId: Long,
    ): ApiResponse<SurpriseRevealDetailResponse> =
        ApiResponse.success(surpriseRevealService.open(userId, fundingId))

    @PostMapping("/{fundingId}/surprise/thank-card")
    fun sendThankCard(
        @AuthenticationPrincipal userId: Long,
        @PathVariable fundingId: Long,
        @Valid @RequestBody request: SendSurpriseThankCardRequest,
    ): ApiResponse<SurpriseThankCardResponse> =
        ApiResponse.success(surpriseRevealService.sendThankCard(userId, fundingId, request))
}
