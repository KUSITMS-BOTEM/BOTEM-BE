package com.botem.domain.funding.controller

import com.botem.domain.funding.controller.docs.MyFundingControllerDocs
import com.botem.domain.funding.dto.response.ClosedFundingListResponse
import com.botem.domain.funding.service.ClosedFundingService
import com.botem.global.response.ApiResponse
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/users/me/fundings")
class MyFundingController(
    private val closedFundingService: ClosedFundingService,
) : MyFundingControllerDocs {

    @GetMapping("/closed")
    override fun getClosedFundings(
        @AuthenticationPrincipal userId: Long,
    ): ApiResponse<ClosedFundingListResponse> =
        ApiResponse.success(closedFundingService.getClosedFundings(userId))
}
