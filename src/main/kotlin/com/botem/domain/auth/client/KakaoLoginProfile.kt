package com.botem.domain.auth.client

// 카카오 로그인 한 번으로 받는 정보. 카카오 액세스 토큰은 클라이언트 밖으로 내보내지 않는다
data class KakaoLoginProfile(
    val userInfo: KakaoUserInfoResponse,
    // 친구 목록 제공에 동의하지 않았거나 조회에 실패하면 비어 있다
    val friendKakaoIds: List<Long>,
)
