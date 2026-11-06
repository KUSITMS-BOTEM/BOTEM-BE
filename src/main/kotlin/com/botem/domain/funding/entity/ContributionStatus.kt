package com.botem.domain.funding.entity

enum class ContributionStatus {
    AWAITING_TRANSFER,
    PENDING_CONFIRMATION,
    CONFIRMED,
    REJECTED,
    CANCELLED,
    MESSAGE_ONLY,
}
