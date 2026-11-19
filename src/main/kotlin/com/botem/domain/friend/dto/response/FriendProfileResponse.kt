package com.botem.domain.friend.dto.response

import com.botem.domain.funding.entity.FundingType
import com.fasterxml.jackson.annotation.JsonFormat
import java.time.LocalDateTime
import java.time.MonthDay

data class FriendProfileResponse(
    val userId: Long,
    val name: String,
    val profileImageUrl: String?,
    @get:JsonFormat(pattern = "MM-dd")
    val birthday: MonthDay?,
    val birthdayDday: Long?,
    val fundings: List<FriendProfileFundingResponse>,
    // 위시는 로그인한 친구(또는 본인)에게만 보여주고, 그 외에는 개수만 알려 잠금 화면을 그린다
    val wishesVisible: Boolean,
    val wishCount: Int,
    val wishes: List<FriendProfileWishResponse>,
)

data class FriendProfileFundingResponse(
    val fundingId: Long,
    val type: FundingType,
    val productName: String,
    val productImageUrl: String,
    val deadline: LocalDateTime,
    val remainingDays: Long,
    val participantCount: Long,
    // "민지님 외 6명 참여중"의 첫 참여자 이름. 참여자가 없으면 null
    val firstParticipantName: String?,
    val confirmedPercentage: Int,
)

data class FriendProfileWishResponse(
    val wishId: Long,
    val productName: String,
    val productImageUrl: String,
    val price: Long,
)
