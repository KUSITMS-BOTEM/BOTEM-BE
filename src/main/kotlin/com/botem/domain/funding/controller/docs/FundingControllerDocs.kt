package com.botem.domain.funding.controller.docs

import com.botem.domain.funding.dto.response.FundingResultResponse
import com.botem.global.response.ApiResponse

interface FundingControllerDocs {
    fun getFundingResult(
        userId: Long,
        fundingId: Long,
    ): ApiResponse<FundingResultResponse>

    fun openFundingResult(
        userId: Long,
        fundingId: Long,
    ): ApiResponse<FundingResultResponse>
}
