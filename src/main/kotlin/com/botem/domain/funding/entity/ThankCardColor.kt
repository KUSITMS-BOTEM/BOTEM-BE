package com.botem.domain.funding.entity

import com.fasterxml.jackson.annotation.JsonCreator

// 감사카드 작성 화면(FUND-BS-007(1))에서 고르는 편지지 색상. 기본은 화면에서 처음 선택된 LIME
enum class ThankCardColor {
    ORANGE,
    LIME,
    PINK,
    BLUE,
    ;

    companion object {
        // Jackson 기본 설정은 숫자를 enum 순서로 받아들이므로({"paperColor": 0} → ORANGE) 문자열 이름만 허용한다
        @JvmStatic
        @JsonCreator
        fun from(value: Any?): ThankCardColor =
            (value as? String)?.let { name -> entries.firstOrNull { it.name == name } }
                ?: throw IllegalArgumentException("지원하지 않는 편지지 색상입니다: $value")
    }
}
