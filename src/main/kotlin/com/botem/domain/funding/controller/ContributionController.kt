package com.botem.domain.funding.controller

import com.botem.domain.funding.controller.docs.ContributionControllerDocs
import com.botem.domain.funding.dto.request.CreateContributionRequest
import com.botem.domain.funding.dto.request.MarkTransferSentRequest
import com.botem.domain.funding.dto.response.ContributionResponse
import com.botem.domain.funding.dto.response.TransferSentResponse
import com.botem.domain.funding.service.ContributionService
import com.botem.global.exception.BusinessException
import com.botem.global.exception.ErrorCode
import com.botem.global.jwt.JwtAuthenticationFilter
import com.botem.global.response.ApiResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

// 비로그인도 허용하지만, 토큰을 보냈는데 유효하지 않으면 재발급할 수 있도록 401로 응답한다
@RestController
@RequestMapping("/api/fundings/{fundingId}/contributions")
class ContributionController(
    private val contributionService: ContributionService,
) : ContributionControllerDocs {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    override fun contribute(
        @AuthenticationPrincipal userId: Long?,
        @RequestAttribute(JwtAuthenticationFilter.ERROR_CODE_ATTRIBUTE, required = false) tokenErrorCode: ErrorCode?,
        @PathVariable fundingId: Long,
        @Valid @RequestBody request: CreateContributionRequest,
    ): ApiResponse<ContributionResponse> {
        tokenErrorCode?.let { throw BusinessException(it) }

        return ApiResponse.success(contributionService.contribute(userId, fundingId, request))
    }

    @PostMapping("/{contributionId}/transfer-sent")
    override fun markTransferSent(
        @AuthenticationPrincipal userId: Long?,
        @RequestAttribute(JwtAuthenticationFilter.ERROR_CODE_ATTRIBUTE, required = false) tokenErrorCode: ErrorCode?,
        @PathVariable fundingId: Long,
        @PathVariable contributionId: Long,
        @Valid @RequestBody request: MarkTransferSentRequest,
    ): ApiResponse<TransferSentResponse> {
        tokenErrorCode?.let { throw BusinessException(it) }

        return ApiResponse.success(
            contributionService.markTransferSent(userId, fundingId, contributionId, request),
        )
    }
}
