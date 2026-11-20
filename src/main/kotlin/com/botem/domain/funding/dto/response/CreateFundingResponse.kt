package com.botem.domain.funding.dto.response

import com.botem.domain.funding.entity.Funding
import com.botem.domain.funding.entity.FundingStatus
import com.botem.domain.funding.entity.FundingType
import java.time.LocalDateTime

data class CreateFundingResponse(
    val fundingId: Long,
    val shareKey: String,
    val type: FundingType,
    val status: FundingStatus,
    val shareUrl: String,
    // 서프라이즈는 받는 사람 생일로 서버가 계산하므로 개설 완료 화면에 그대로 보여준다
    val deadline: LocalDateTime,
    val revealAt: LocalDateTime?,
) {
    companion object {
        fun from(funding: Funding, shareUrl: String): CreateFundingResponse =
            CreateFundingResponse(
                fundingId = funding.id,
                shareKey = funding.shareKey,
                type = funding.type,
                status = funding.status,
                shareUrl = shareUrl,
                deadline = funding.deadline,
                revealAt = funding.revealAt,
            )
    }
}
