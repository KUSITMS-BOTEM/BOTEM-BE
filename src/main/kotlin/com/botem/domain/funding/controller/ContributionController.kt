package com.botem.domain.funding.controller

import com.botem.domain.funding.controller.docs.ContributionControllerDocs
import com.botem.domain.funding.dto.request.CreateContributionRequest
import com.botem.domain.funding.dto.request.CreateMessageContributionRequest
import com.botem.domain.funding.dto.request.MarkTransferSentRequest
import com.botem.domain.funding.dto.response.ContributionResponse
import com.botem.domain.funding.dto.response.MessageContributionResponse
import com.botem.domain.funding.dto.response.TransferSentResponse
import com.botem.domain.funding.service.ContributionService
import com.botem.global.response.ApiResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

// 비로그인도 허용하며, 보낸 토큰이 유효하지 않으면 SecurityConfig에서 401로 응답한다
@RestController
@RequestMapping("/api/fundings/{fundingId}/contributions")
class ContributionController(
    private val contributionService: ContributionService,
) : ContributionControllerDocs {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    override fun contribute(
        @AuthenticationPrincipal userId: Long?,
        @PathVariable fundingId: Long,
        @Valid @RequestBody request: CreateContributionRequest,
    ): ApiResponse<ContributionResponse> =
        ApiResponse.success(contributionService.contribute(userId, fundingId, request))

    @PostMapping("/message-only")
    @ResponseStatus(HttpStatus.CREATED)
    override fun leaveMessage(
        @AuthenticationPrincipal userId: Long?,
        @PathVariable fundingId: Long,
        @Valid @RequestBody request: CreateMessageContributionRequest,
    ): ApiResponse<MessageContributionResponse> =
        ApiResponse.success(contributionService.leaveMessage(userId, fundingId, request))

    @PostMapping("/{contributionId}/transfer-sent")
    override fun markTransferSent(
        @AuthenticationPrincipal userId: Long?,
        @PathVariable fundingId: Long,
        @PathVariable contributionId: Long,
        @Valid @RequestBody request: MarkTransferSentRequest,
    ): ApiResponse<TransferSentResponse> =
        ApiResponse.success(contributionService.markTransferSent(userId, fundingId, contributionId, request))
}
