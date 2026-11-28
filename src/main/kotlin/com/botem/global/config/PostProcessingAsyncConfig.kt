package com.botem.global.config

import com.botem.global.property.PostProcessingAsyncProperties
import java.util.concurrent.ThreadPoolExecutor
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor

@Configuration
@EnableAsync
class PostProcessingAsyncConfig {

    private val log = LoggerFactory.getLogger(javaClass)

    // 요청 응답에 필요 없는 외부 정리 작업만 사용한다. 포화 시 요청 스레드에서 대신 실행하지 않고 버리며,
    // 작업 유실을 로그(Sentry)로 남긴다. 이미지 삭제는 기존에도 재시도 없이 로그만 남기는 정책이다.
    @Bean(POST_PROCESSING_TASK_EXECUTOR)
    fun postProcessingTaskExecutor(properties: PostProcessingAsyncProperties): ThreadPoolTaskExecutor =
        ThreadPoolTaskExecutor().apply {
            corePoolSize = properties.corePoolSize
            maxPoolSize = properties.maxPoolSize
            setQueueCapacity(properties.queueCapacity)
            setThreadNamePrefix("post-processing-")
            setWaitForTasksToCompleteOnShutdown(true)
            setAwaitTerminationSeconds(properties.awaitTermination.toSeconds().toInt())
            setRejectedExecutionHandler { _, executor ->
                val pool = executor as ThreadPoolExecutor
                log.error(
                    "후처리 비동기 작업이 포화되어 실행하지 못했습니다. activeCount={}, poolSize={}, queueSize={}",
                    pool.activeCount,
                    pool.poolSize,
                    pool.queue.size,
                )
            }
        }

    companion object {
        const val POST_PROCESSING_TASK_EXECUTOR = "postProcessingTaskExecutor"
    }
}
