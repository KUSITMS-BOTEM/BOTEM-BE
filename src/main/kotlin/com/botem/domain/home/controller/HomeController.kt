package com.botem.domain.home.controller

import com.botem.domain.home.controller.docs.HomeControllerDocs
import com.botem.domain.home.dto.response.GiftVerificationBannerResponse
import com.botem.domain.home.dto.response.GiftVerificationTargetListResponse
import com.botem.domain.home.dto.response.HomeFundingListResponse
import com.botem.domain.home.dto.response.HomeNoFundingResponse
import com.botem.domain.home.dto.response.SurpriseRevealCardListResponse
import com.botem.domain.home.service.HomeGiftVerificationService
import com.botem.domain.home.service.HomeFundingService
import com.botem.domain.home.service.HomeNoFundingService
import com.botem.domain.home.service.HomeSurpriseRevealService
import com.botem.global.response.ApiResponse
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/home")
class HomeController(
    private val homeFundingService: HomeFundingService,
    private val homeNoFundingService: HomeNoFundingService,
    private val homeGiftVerificationService: HomeGiftVerificationService,
    private val homeSurpriseRevealService: HomeSurpriseRevealService,
) : HomeControllerDocs {

    @GetMapping("/fundings")
    override fun getHomeFundings(
        @AuthenticationPrincipal userId: Long,
    ): ApiResponse<HomeFundingListResponse> =
        ApiResponse.success(homeFundingService.getHomeFundings(userId))

    @GetMapping("/no-funding")
    override fun getNoFundingHome(
        @AuthenticationPrincipal userId: Long,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int,
    ): ApiResponse<HomeNoFundingResponse> =
        ApiResponse.success(homeNoFundingService.getNoFundingHome(userId, page, size))

    @GetMapping("/gift-verifications/banner")
    override fun getGiftVerificationBanner(
        @AuthenticationPrincipal userId: Long,
    ): ApiResponse<GiftVerificationBannerResponse> =
        ApiResponse.success(homeGiftVerificationService.getBanner(userId))

    @GetMapping("/gift-verifications")
    override fun getGiftVerificationTargets(
        @AuthenticationPrincipal userId: Long,
    ): ApiResponse<GiftVerificationTargetListResponse> =
        ApiResponse.success(homeGiftVerificationService.getTargets(userId))

    @GetMapping("/surprise-reveals")
    override fun getSurpriseRevealCards(
        @AuthenticationPrincipal userId: Long,
    ): ApiResponse<SurpriseRevealCardListResponse> =
        ApiResponse.success(homeSurpriseRevealService.getCards(userId))
}
