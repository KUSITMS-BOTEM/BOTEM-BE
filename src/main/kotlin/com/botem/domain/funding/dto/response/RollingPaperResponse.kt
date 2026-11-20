package com.botem.domain.funding.dto.response

// 생일 펀딩 개설자가 팡을 연 뒤 보는 롤링페이퍼 ("생일 축하해요 지유님! 총 5개의 메시지가 도착했어요")
data class RollingPaperResponse(
    val fundingId: Long,
    val celebrantName: String,
    val messageCount: Int,
    val messages: List<RollingPaperMessageResponse>,
)
