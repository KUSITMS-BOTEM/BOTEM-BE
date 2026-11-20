package com.botem.domain.funding.controller

import com.botem.domain.funding.controller.docs.MyContributionControllerDocs
import com.botem.domain.funding.dto.request.UpdateMyContributionRequest
import com.botem.domain.funding.dto.response.MyContributionListResponse
import com.botem.domain.funding.dto.response.MyContributionResponse
import com.botem.domain.funding.service.FundingShareKeyResolver
import com.botem.domain.funding.service.MyContributionService
import com.botem.global.response.ApiResponse
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/fundings/share/{shareKey}/contributions")
class MyContributionController(
    private val fundingShareKeyResolver: FundingShareKeyResolver,
    private val myContributionService: MyContributionService,
) : MyContributionControllerDocs {

    @GetMapping("/me")
    override fun getMyContributions(
        @AuthenticationPrincipal userId: Long,
        @PathVariable shareKey: String,
    ): ApiResponse<MyContributionListResponse> =
        ApiResponse.success(myContributionService.getMyContributions(userId, fundingShareKeyResolver.fundingIdOf(shareKey)))

    @PatchMapping("/{contributionId}")
    override fun updateMyContribution(
        @AuthenticationPrincipal userId: Long,
        @PathVariable shareKey: String,
        @PathVariable contributionId: Long,
        @Valid @RequestBody request: UpdateMyContributionRequest,
    ): ApiResponse<MyContributionResponse> =
        ApiResponse.success(myContributionService.updateMyContribution(userId, fundingShareKeyResolver.fundingIdOf(shareKey), contributionId, request))
}
