package com.botem.domain.home.controller

import com.botem.domain.home.controller.docs.HomeControllerDocs
import com.botem.domain.home.dto.response.HomeFundingListResponse
import com.botem.domain.home.service.HomeFundingService
import com.botem.global.response.ApiResponse
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/home")
class HomeController(
    private val homeFundingService: HomeFundingService,
) : HomeControllerDocs {

    @GetMapping("/fundings")
    override fun getHomeFundings(
        @AuthenticationPrincipal userId: Long,
    ): ApiResponse<HomeFundingListResponse> =
        ApiResponse.success(homeFundingService.getHomeFundings(userId))
}
