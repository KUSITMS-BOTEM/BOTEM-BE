package com.botem.global.config

import com.botem.global.property.RateLimitProperties
import com.botem.global.ratelimit.RateLimitCounter
import com.botem.global.ratelimit.RateLimitFilter
import com.botem.global.ratelimit.RedisRateLimitCounter
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.core.StringRedisTemplate
import tools.jackson.databind.json.JsonMapper
import java.time.Clock

@Configuration
class RateLimitConfig {

    @Bean
    fun rateLimitCounter(redisTemplate: StringRedisTemplate): RateLimitCounter = RedisRateLimitCounter(redisTemplate)

    // Spring Security(CORS 헤더를 붙임) 바로 다음에 실행한다. 먼저 실행하면 429 응답에 CORS 헤더가 없어
    // 브라우저가 응답을 막고, 프론트는 429 대신 네트워크 오류만 보게 된다. 컨트롤러·본문 처리 전에는 끊는다
    @Bean
    fun rateLimitFilter(
        properties: RateLimitProperties,
        jsonMapper: JsonMapper,
        clock: Clock,
        counter: RateLimitCounter,
    ): FilterRegistrationBean<RateLimitFilter> =
        FilterRegistrationBean(RateLimitFilter(properties, jsonMapper, clock, counter)).apply {
            order = SecurityFilterProperties.DEFAULT_FILTER_ORDER + 1
        }
}
