package com.botem.global.property

import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Positive
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated
import java.time.Duration

@Validated
@ConfigurationProperties(prefix = "post-processing.async")
data class PostProcessingAsyncProperties(
    @field:Positive(message = "후처리 비동기 실행기의 기본 스레드 수는 1 이상이어야 합니다")
    val corePoolSize: Int = 2,
    @field:Positive(message = "후처리 비동기 실행기의 최대 스레드 수는 1 이상이어야 합니다")
    val maxPoolSize: Int = 4,
    @field:Min(value = 0, message = "후처리 비동기 실행기의 대기 큐 크기는 0 이상이어야 합니다")
    val queueCapacity: Int = 100,
    val awaitTermination: Duration = Duration.ofSeconds(10),
) {
    @get:AssertTrue(message = "후처리 비동기 실행기의 최대 스레드 수는 기본 스레드 수 이상이어야 합니다")
    val isPoolSizeValid: Boolean
        get() = maxPoolSize >= corePoolSize

    @get:AssertTrue(message = "후처리 비동기 실행기의 종료 대기 시간은 1초 이상이어야 합니다")
    val isAwaitTerminationValid: Boolean
        get() = awaitTermination >= Duration.ofSeconds(1)
}
