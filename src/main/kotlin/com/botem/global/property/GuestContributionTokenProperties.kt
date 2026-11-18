package com.botem.global.property

import jakarta.validation.constraints.AssertTrue
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated
import java.time.Duration

@Validated
@ConfigurationProperties(prefix = "guest-contribution-token")
data class GuestContributionTokenProperties(
    val accessTokenExpiration: Duration,
) {
    @get:AssertTrue(message = "비회원 보탬 접근 토큰 만료 시간은 0보다 커야 합니다")
    val isAccessTokenExpirationPositive: Boolean
        get() = !accessTokenExpiration.isZero && !accessTokenExpiration.isNegative
}
