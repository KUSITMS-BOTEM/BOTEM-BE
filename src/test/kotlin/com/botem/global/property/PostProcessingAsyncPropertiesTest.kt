package com.botem.global.property

import jakarta.validation.Validation
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class PostProcessingAsyncPropertiesTest {

    private val validator = Validation.buildDefaultValidatorFactory().validator

    @Test
    fun `후처리 실행기 스레드 수와 종료 대기 시간의 경계를 검증한다`() {
        val invalidProperties = listOf(
            PostProcessingAsyncProperties(corePoolSize = 0),
            PostProcessingAsyncProperties(maxPoolSize = 0),
            PostProcessingAsyncProperties(corePoolSize = 2, maxPoolSize = 1),
            PostProcessingAsyncProperties(queueCapacity = -1),
            PostProcessingAsyncProperties(awaitTermination = Duration.ZERO),
        )

        invalidProperties.forEach { properties ->
            assertTrue(validator.validate(properties).isNotEmpty(), properties.toString())
        }
    }

    @Test
    fun `유효한 후처리 실행기 설정을 허용한다`() {
        val properties = PostProcessingAsyncProperties(
            corePoolSize = 2,
            maxPoolSize = 4,
            queueCapacity = 100,
            awaitTermination = Duration.ofSeconds(10),
        )

        assertEquals(emptySet(), validator.validate(properties))
    }
}
