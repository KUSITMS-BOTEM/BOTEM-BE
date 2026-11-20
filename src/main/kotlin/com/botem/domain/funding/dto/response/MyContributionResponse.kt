package com.botem.domain.funding.dto.response

import com.botem.domain.funding.entity.ContributionStatus
import java.time.LocalDateTime

data class MyContributionListResponse(
    // 마감일 전이면 메시지·편지지·이름을 수정할 수 있다 ("마감일까지 수정할 수 있어요")
    val editable: Boolean,
    val contributions: List<MyContributionResponse>,
)

data class MyContributionResponse(
    val contributionId: Long,
    val participantName: String,
    val amount: Long,
    val status: ContributionStatus,
    // 팡!(서프라이즈는 선물 준비 완료) 때 확인되지 않아 결과에서 빠진 확인 대기 보탬 ("확인되지 않은 채 마감됐어요")
    val excludedFromResult: Boolean,
    val message: String?,
    val paperTemplateCode: String?,
    val decorationCode: String?,
    val photoUrl: String?,
    val voiceUrl: String?,
    val voiceDurationSeconds: Int?,
    val voiceTranscript: String?,
    val participatedAt: LocalDateTime,
    // 송금 전이거나 개설자가 못 받았다고 한 보탬에만 다시 송금할 계좌를 보여준다
    val payoutAccount: ContributionPayoutAccountResponse?,
)
