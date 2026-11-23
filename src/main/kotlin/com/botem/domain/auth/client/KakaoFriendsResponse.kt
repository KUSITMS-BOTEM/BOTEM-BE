package com.botem.domain.auth.client

import com.fasterxml.jackson.annotation.JsonProperty

data class KakaoFriendsResponse(
    val elements: List<KakaoFriend> = emptyList(),
    // 다음 페이지가 없으면 null
    @JsonProperty("after_url")
    val afterUrl: String? = null,
)

// id는 친구의 카카오 회원번호로, 보탬 users.kakao_id와 같은 값이다
data class KakaoFriend(
    val id: Long,
)
