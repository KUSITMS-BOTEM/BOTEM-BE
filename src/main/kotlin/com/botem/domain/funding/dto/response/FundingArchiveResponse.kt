package com.botem.domain.funding.dto.response

import com.botem.domain.funding.entity.FundingType
import java.time.LocalDateTime

data class FundingArchiveResponse(
    val tabCounts: FundingArchiveTabCounts,
    val years: List<FundingArchiveYearResponse>,
)

data class FundingArchiveTabCounts(
    val all: Int,
    val received: Int,
    val participated: Int,
)

data class FundingArchiveYearResponse(
    val year: Int,
    val fundingCount: Int,
    val fundings: List<FundingArchiveCardResponse>,
)

data class FundingArchiveCardResponse(
    val fundingId: Long,
    val relationship: FundingArchiveRelationship,
    val fundingType: FundingType,
    val opener: FundingArchiveUserResponse,
    val celebrantName: String,
    val deadline: LocalDateTime,
    val productName: String,
    val productImageUrl: String,
    val rollingPaperCount: Long,
    val representativeMessage: FundingArchiveRepresentativeMessageResponse?,
)

data class FundingArchiveUserResponse(
    val name: String,
    val profileImageUrl: String?,
)

data class FundingArchiveRepresentativeMessageResponse(
    val writerName: String,
    val writerProfileImageUrl: String?,
    val content: String?,
    val paperTemplateCode: String,
    val hasVoice: Boolean,
)

enum class FundingArchiveTab {
    ALL,
    RECEIVED,
    PARTICIPATED,
}

enum class FundingArchiveRelationship {
    OPENED,
    RECEIVED,
    PARTICIPATED,
}
