package com.botem.domain.funding.controller

import com.botem.domain.funding.controller.docs.ContributionThankCardControllerDocs
import com.botem.domain.funding.dto.request.SendContributionThankCardRequest
import com.botem.domain.funding.dto.response.ContributionThankCardDetailResponse
import com.botem.domain.funding.dto.response.ContributionThankCardResponse
import com.botem.domain.funding.dto.response.SurpriseThankCardFriendsResponse
import com.botem.domain.funding.service.ContributionThankCardService
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
@RequestMapping("/api")
class ContributionThankCardController(
    private val contributionThankCardService: ContributionThankCardService,
) : ContributionThankCardControllerDocs {

    @PostMapping("/fundings/{fundingId}/contributions/{contributionId}/thank-card")
    override fun sendThankCard(
        @AuthenticationPrincipal userId: Long,
        @PathVariable fundingId: Long,
        @PathVariable contributionId: Long,
        @Valid @RequestBody request: SendContributionThankCardRequest,
    ): ApiResponse<ContributionThankCardResponse> =
        ApiResponse.success(contributionThankCardService.send(userId, fundingId, contributionId, request))

    @GetMapping("/contributions/{contributionId}/thank-card")
    override fun getMyThankCard(
        @AuthenticationPrincipal userId: Long,
        @PathVariable contributionId: Long,
    ): ApiResponse<ContributionThankCardDetailResponse> =
        ApiResponse.success(contributionThankCardService.getForParticipant(userId, contributionId))

    @GetMapping("/thank-cards/{shareKey}")
    override fun getSharedThankCard(
        @PathVariable shareKey: String,
    ): ApiResponse<ContributionThankCardDetailResponse> =
        ApiResponse.success(contributionThankCardService.getByShareKey(shareKey))

    @GetMapping("/fundings/{fundingId}/surprise/thank-card-friends")
    override fun getSurpriseThankCardFriends(
        @AuthenticationPrincipal userId: Long,
        @PathVariable fundingId: Long,
    ): ApiResponse<SurpriseThankCardFriendsResponse> =
        ApiResponse.success(contributionThankCardService.getSurpriseThankCardFriends(userId, fundingId))
}
