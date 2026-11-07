package com.botem.domain.product.entity

enum class ProductCategory(
    val displayName: String,
) {
    EARPHONE_HEADPHONE("이어폰·헤드폰"),
    SMART_DEVICE_GAME("스마트기기·게임"),
    HAIR_BEAUTY_DEVICE("헤어·뷰티 기기"),
    SKINCARE_MAKEUP("스킨케어·메이크업"),
    PERFUME("향수"),
    TUMBLER_LIVING("텀블러·리빙"),
}
