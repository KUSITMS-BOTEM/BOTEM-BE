package com.botem.global.config

import org.springframework.cache.annotation.CachingConfigurer
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.interceptor.CacheErrorHandler
import org.springframework.cache.interceptor.LoggingCacheErrorHandler
import org.springframework.context.annotation.Configuration

@Configuration
@EnableCaching
class CacheConfig : CachingConfigurer {

    // 캐시(Redis) 오류는 로그만 남기고 원래 조회(DB)로 진행한다. 캐시 장애가 서비스 장애로 번지지 않게 한다
    override fun errorHandler(): CacheErrorHandler = LoggingCacheErrorHandler()
}
