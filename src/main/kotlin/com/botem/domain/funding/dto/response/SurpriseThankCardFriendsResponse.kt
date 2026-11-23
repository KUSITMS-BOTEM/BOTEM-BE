package com.botem.domain.funding.dto.response

import java.time.LocalDate

data class SurpriseThankCardFriendsResponse(
    val fundingId: Long,
    val friends: List<SurpriseThankCardFriendResponse>,
)

data class SurpriseThankCardFriendResponse(
    val contributionId: Long,
    val name: String,
    val profileImageUrl: String?,
    val organizer: Boolean,
    val messageTypes: List<RollingPaperMessageType>,
    val contributedDate: LocalDate,
    val thankCardSent: Boolean,
)

enum class RollingPaperMessageType {
    TEXT,
    PHOTO,
    VOICE,
    STICKER,
}
