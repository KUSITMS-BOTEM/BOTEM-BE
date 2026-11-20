package com.botem.domain.home.dto.response

// 홈 서프라이즈 카드가 어느 화면으로 갈지 정한다
enum class SurpriseStage {
    // 마감 전 진행 중
    IN_PROGRESS,

    // 마감(또는 조기 마감)했지만 주최자가 선물 준비를 끝내지 않음 (SUR-PG-007)
    GIFT_PREPARING,

    // 선물 준비를 끝내고 공개를 기다림 (SUR-PG-008)
    GIFT_PREPARED,
}
