package com.botem.global.property

import jakarta.validation.constraints.AssertTrue
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated
import java.time.Duration

@Validated
@ConfigurationProperties(prefix = "guest-contribution-token")
data class GuestContributionTokenProperties(
    val accessTokenExpiration: Duration,
    val issuer: String = DEFAULT_ISSUER,
    val audience: String = DEFAULT_AUDIENCE,
) {
    @get:AssertTrue(message = "비회원 보탬 접근 토큰 만료 시간은 1초 이상이어야 합니다")
    val isAccessTokenExpirationValid: Boolean
        get() = accessTokenExpiration >= Duration.ofSeconds(1)

    companion object {
        const val DEFAULT_ISSUER = "botem"
        const val DEFAULT_AUDIENCE = "guest-contribution"
    }
}
