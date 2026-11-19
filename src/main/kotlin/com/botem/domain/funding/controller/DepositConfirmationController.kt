package com.botem.domain.funding.controller

import com.botem.domain.funding.controller.docs.DepositConfirmationControllerDocs
import com.botem.domain.funding.dto.request.DecideContributionsRequest
import com.botem.domain.funding.dto.request.UndoContributionDecisionsRequest
import com.botem.domain.funding.dto.response.ConfirmAllContributionsResponse
import com.botem.domain.funding.dto.response.ContributionDecisionResponse
import com.botem.domain.funding.dto.response.ContributionDecisionsResponse
import com.botem.domain.funding.dto.response.PendingContributionListResponse
import com.botem.domain.funding.service.DepositConfirmationService
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
@RequestMapping("/api/fundings/{fundingId}/contributions")
class DepositConfirmationController(
    private val depositConfirmationService: DepositConfirmationService,
) : DepositConfirmationControllerDocs {

    @GetMapping("/pending")
    override fun getPendingContributions(
        @AuthenticationPrincipal userId: Long,
        @PathVariable fundingId: Long,
    ): ApiResponse<PendingContributionListResponse> =
        ApiResponse.success(depositConfirmationService.getPendingContributions(userId, fundingId))

    @PostMapping("/{contributionId}/confirm")
    override fun confirm(
        @AuthenticationPrincipal userId: Long,
        @PathVariable fundingId: Long,
        @PathVariable contributionId: Long,
    ): ApiResponse<ContributionDecisionResponse> =
        ApiResponse.success(depositConfirmationService.confirm(userId, fundingId, contributionId))

    @PostMapping("/{contributionId}/reject")
    override fun reject(
        @AuthenticationPrincipal userId: Long,
        @PathVariable fundingId: Long,
        @PathVariable contributionId: Long,
    ): ApiResponse<ContributionDecisionResponse> =
        ApiResponse.success(depositConfirmationService.reject(userId, fundingId, contributionId))

    @PostMapping("/confirm-all")
    override fun confirmAll(
        @AuthenticationPrincipal userId: Long,
        @PathVariable fundingId: Long,
    ): ApiResponse<ConfirmAllContributionsResponse> =
        ApiResponse.success(depositConfirmationService.confirmAll(userId, fundingId))

    @PostMapping("/decisions")
    override fun decide(
        @AuthenticationPrincipal userId: Long,
        @PathVariable fundingId: Long,
        @Valid @RequestBody request: DecideContributionsRequest,
    ): ApiResponse<ContributionDecisionsResponse> =
        ApiResponse.success(depositConfirmationService.decide(userId, fundingId, request))

    @PostMapping("/decisions/undo")
    override fun undo(
        @AuthenticationPrincipal userId: Long,
        @PathVariable fundingId: Long,
        @Valid @RequestBody request: UndoContributionDecisionsRequest,
    ): ApiResponse<ContributionDecisionsResponse> =
        ApiResponse.success(depositConfirmationService.undo(userId, fundingId, request))
}
