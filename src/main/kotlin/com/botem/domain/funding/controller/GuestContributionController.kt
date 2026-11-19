package com.botem.domain.funding.controller

import com.botem.domain.funding.controller.docs.GuestContributionControllerDocs
import com.botem.domain.funding.dto.request.AccessGuestContributionRequest
import com.botem.domain.funding.dto.request.UpdateGuestContributionRequest
import com.botem.domain.funding.dto.response.GuestContributionResponse
import com.botem.domain.funding.dto.response.UpdatedGuestContributionResponse
import com.botem.domain.funding.exception.FundingErrorCode
import com.botem.domain.funding.service.GuestContributionAccessTokenProvider
import com.botem.domain.funding.service.GuestContributionService
import com.botem.global.exception.BusinessException
import com.botem.global.response.ApiResponse
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/fundings/{fundingId}/contributions/guest")
class GuestContributionController(
    private val guestContributionService: GuestContributionService,
) : GuestContributionControllerDocs {

    @PostMapping("/access")
    override fun access(
        @PathVariable fundingId: Long,
        @Valid @RequestBody request: AccessGuestContributionRequest,
    ): ApiResponse<GuestContributionResponse> =
        ApiResponse.success(guestContributionService.access(fundingId, request))

    @PatchMapping
    override fun update(
        @PathVariable fundingId: Long,
        @RequestHeader(GuestContributionAccessTokenProvider.HEADER_NAME, required = false) accessToken: String?,
        @Valid @RequestBody request: UpdateGuestContributionRequest,
    ): ApiResponse<UpdatedGuestContributionResponse> =
        ApiResponse.success(guestContributionService.update(fundingId, requireAccessToken(accessToken), request))

    @DeleteMapping
    override fun cancel(
        @PathVariable fundingId: Long,
        @RequestHeader(GuestContributionAccessTokenProvider.HEADER_NAME, required = false) accessToken: String?,
    ): ApiResponse<Nothing> {
        guestContributionService.cancel(fundingId, requireAccessToken(accessToken))

        return ApiResponse.success()
    }

    private fun requireAccessToken(accessToken: String?): String =
        accessToken?.takeIf { it.isNotBlank() }
            ?: throw BusinessException(FundingErrorCode.CONTRIBUTION_GUEST_ACCESS_TOKEN_INVALID)
}
