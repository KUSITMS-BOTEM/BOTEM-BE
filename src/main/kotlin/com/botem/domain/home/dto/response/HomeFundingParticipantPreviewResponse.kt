package com.botem.domain.home.dto.response

import com.botem.domain.funding.repository.projection.FundingParticipantPreviewProjection

data class HomeFundingParticipantPreviewResponse(
    val participantName: String,
    val profileImageUrl: String?,
) {
    companion object {
        fun from(preview: FundingParticipantPreviewProjection): HomeFundingParticipantPreviewResponse =
            HomeFundingParticipantPreviewResponse(
                participantName = preview.participantName,
                profileImageUrl = preview.profileImageUrl,
            )
    }
}
