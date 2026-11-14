package com.botem.domain.funding.controller

import com.botem.domain.funding.controller.docs.FundingControllerDocs
import com.botem.domain.funding.dto.response.FundingResultResponse
import com.botem.domain.funding.service.FundingResultService
import com.botem.global.response.ApiResponse
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/fundings")
class FundingController(
    private val fundingResultService: FundingResultService,
) : FundingControllerDocs {

    @GetMapping("/{fundingId}/result")
    override fun getFundingResult(
        @AuthenticationPrincipal userId: Long,
        @PathVariable fundingId: Long,
    ): ApiResponse<FundingResultResponse> =
        ApiResponse.success(fundingResultService.getResult(userId, fundingId))
}
