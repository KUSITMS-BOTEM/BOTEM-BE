package com.botem.global.config

import com.botem.domain.user.service.ProfileImageService
import com.botem.domain.user.service.UserWithdrawnEvent
import com.botem.global.property.PostProcessingAsyncProperties
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.scheduling.annotation.Async
import org.springframework.transaction.event.TransactionalEventListener

class PostProcessingAsyncConfigTest {

    private val config = PostProcessingAsyncConfig()

    @Test
    fun `포화된 후처리 실행기는 요청 스레드를 점유하지 않고 거부 지표를 기록한다`() {
        val meterRegistry = SimpleMeterRegistry()
        val executor = config.postProcessingTaskExecutor(
            PostProcessingAsyncProperties(
                corePoolSize = 1,
                maxPoolSize = 1,
                queueCapacity = 1,
                awaitTermination = Duration.ofSeconds(1),
            ),
            meterRegistry,
        )
        executor.initialize()
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)

        try {
            executor.execute {
                started.countDown()
                release.await()
            }
            assertTrue(started.await(1, TimeUnit.SECONDS))

            executor.execute { }
            executor.execute { error("포화 상태에서는 실행되면 안 됩니다") }

            assertEquals(
                1.0,
                meterRegistry.get("post.processing.tasks.rejected")
                    .tag("executor", PostProcessingAsyncConfig.POST_PROCESSING_TASK_EXECUTOR)
                    .counter()
                    .count(),
            )
        } finally {
            release.countDown()
            executor.shutdown()
            meterRegistry.close()
        }
    }

    @Test
    fun `프로필 이미지 삭제는 커밋 후 전용 비동기 실행기로 처리한다`() {
        val method = ProfileImageService::class.java.getMethod("deleteAllImages", UserWithdrawnEvent::class.java)

        val async = requireNotNull(method.getAnnotation(Async::class.java))
        assertTrue(async.value == PostProcessingAsyncConfig.POST_PROCESSING_TASK_EXECUTOR)
        assertNotNull(method.getAnnotation(TransactionalEventListener::class.java))
    }
}
