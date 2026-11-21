package com.botem.domain.funding.controller

import com.botem.domain.funding.controller.docs.MyFundingControllerDocs
import com.botem.domain.funding.dto.response.FundingArchiveResponse
import com.botem.domain.funding.dto.response.FundingArchiveTab
import com.botem.domain.funding.service.FundingArchiveService
import com.botem.global.response.ApiResponse
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/users/me/fundings")
class MyFundingController(
    private val fundingArchiveService: FundingArchiveService,
) : MyFundingControllerDocs {

    @GetMapping("/closed")
    override fun getFundingArchive(
        @AuthenticationPrincipal userId: Long,
        @RequestParam(defaultValue = "ALL") tab: FundingArchiveTab,
    ): ApiResponse<FundingArchiveResponse> =
        ApiResponse.success(fundingArchiveService.getArchive(userId, tab))
}
