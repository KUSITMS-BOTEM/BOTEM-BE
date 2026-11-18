package com.botem.domain.funding.controller

import com.botem.domain.funding.controller.docs.FundingControllerDocs
import com.botem.domain.funding.dto.request.CreateBirthdayFundingRequest
import com.botem.domain.funding.dto.response.CreateFundingResponse
import com.botem.domain.funding.dto.response.FundingDetailResponse
import com.botem.domain.funding.dto.response.FundingResultResponse
import com.botem.domain.funding.dto.response.OwnerFundingDetailResponse
import com.botem.domain.funding.service.FundingCreationService
import com.botem.domain.funding.service.FundingDetailService
import com.botem.domain.funding.service.FundingResultService
import com.botem.domain.funding.service.OwnerFundingDetailService
import com.botem.global.exception.BusinessException
import com.botem.global.exception.ErrorCode
import com.botem.global.jwt.JwtAuthenticationFilter
import com.botem.global.response.ApiResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/fundings")
class FundingController(
    private val fundingCreationService: FundingCreationService,
    private val fundingResultService: FundingResultService,
    private val fundingDetailService: FundingDetailService,
    private val ownerFundingDetailService: OwnerFundingDetailService,
) : FundingControllerDocs {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    override fun createBirthdayFunding(
        @AuthenticationPrincipal userId: Long,
        @Valid @RequestBody request: CreateBirthdayFundingRequest,
    ): ApiResponse<CreateFundingResponse> =
        ApiResponse.success(fundingCreationService.createBirthdayFunding(userId, request))

    // 비로그인도 허용하지만, 토큰을 보냈는데 유효하지 않으면 재발급할 수 있도록 401로 응답한다
    @GetMapping("/{fundingId}")
    override fun getFundingDetail(
        @AuthenticationPrincipal userId: Long?,
        @RequestAttribute(JwtAuthenticationFilter.ERROR_CODE_ATTRIBUTE, required = false) tokenErrorCode: ErrorCode?,
        @PathVariable fundingId: Long,
    ): ApiResponse<FundingDetailResponse> {
        if (tokenErrorCode != null) {
            throw BusinessException(tokenErrorCode)
        }

        return ApiResponse.success(fundingDetailService.getDetail(userId, fundingId))
    }

    @GetMapping("/{fundingId}/management")
    override fun getFundingManagementDetail(
        @AuthenticationPrincipal userId: Long,
        @PathVariable fundingId: Long,
    ): ApiResponse<OwnerFundingDetailResponse> =
        ApiResponse.success(ownerFundingDetailService.getManagementDetail(userId, fundingId))

    @GetMapping("/{fundingId}/result")
    override fun getFundingResult(
        @AuthenticationPrincipal userId: Long,
        @PathVariable fundingId: Long,
    ): ApiResponse<FundingResultResponse> =
        ApiResponse.success(fundingResultService.getResult(userId, fundingId))

    @PostMapping("/{fundingId}/result/open")
    override fun openFundingResult(
        @AuthenticationPrincipal userId: Long,
        @PathVariable fundingId: Long,
    ): ApiResponse<FundingResultResponse> =
        ApiResponse.success(fundingResultService.openResult(userId, fundingId))
}
