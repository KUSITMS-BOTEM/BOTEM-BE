package com.botem.domain.funding.repository.projection

interface FundingParticipantPreviewProjection {
    val fundingId: Long
    val participantUserId: Long?
    val participantName: String
    val profileImageUrl: String?
}
