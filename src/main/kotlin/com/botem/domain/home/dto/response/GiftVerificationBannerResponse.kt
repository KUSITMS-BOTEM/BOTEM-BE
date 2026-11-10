package com.botem.domain.home.dto.response

data class GiftVerificationBannerResponse(
    val visible: Boolean,
    val pendingCount: Long,
) {
    companion object {
        fun from(pendingCount: Long): GiftVerificationBannerResponse =
            GiftVerificationBannerResponse(
                visible = pendingCount > 0L,
                pendingCount = pendingCount,
            )
    }
}
