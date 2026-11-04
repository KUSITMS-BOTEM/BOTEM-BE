package com.botem.global.handler

import com.botem.global.exception.BusinessException
import com.botem.global.exception.CommonErrorCode
import com.botem.global.notification.DiscordErrorNotifier
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

class GlobalExceptionHandlerTest {

    private val notifier: DiscordErrorNotifier = mock(DiscordErrorNotifier::class.java)

    private val mockMvc: MockMvc = MockMvcBuilders
        .standaloneSetup(TestController())
        .setControllerAdvice(GlobalExceptionHandler(notifier))
        .build()

    @Test
    fun `지원하지 않는 메서드는 405로 응답하고 알림을 보내지 않는다`() {
        mockMvc.perform(post("/test/error"))
            .andExpect(status().isMethodNotAllowed)
            .andExpect(jsonPath("$.error.code").value("COMMON_405"))

        verify(notifier, never()).notify(anyThrowable(), any())
    }

    @Test
    fun `처리되지 않은 예외는 500으로 응답하고 알림을 보낸다`() {
        mockMvc.perform(get("/test/error"))
            .andExpect(status().isInternalServerError)
            .andExpect(jsonPath("$.error.code").value("COMMON_500"))

        verify(notifier).notify(anyThrowable(), any())
    }

    @Test
    fun `500 상태의 BusinessException은 알림을 보내고 4xx는 보내지 않는다`() {
        mockMvc.perform(get("/test/business-500"))
            .andExpect(status().isInternalServerError)
            .andExpect(jsonPath("$.error.code").value("COMMON_500"))

        mockMvc.perform(get("/test/business-400"))
            .andExpect(status().isBadRequest)

        verify(notifier, times(1)).notify(anyThrowable(), any())
    }

    // Kotlin non-null 파라미터에 Mockito any()의 null이 전달되지 않도록 대체 값 반환
    private fun anyThrowable(): Throwable = any(Throwable::class.java) ?: IllegalStateException()

    @RestController
    class TestController {
        @GetMapping("/test/error")
        fun error(): String = throw IllegalStateException("boom")

        @GetMapping("/test/business-500")
        fun business500(): String = throw BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR)

        @GetMapping("/test/business-400")
        fun business400(): String = throw BusinessException(CommonErrorCode.INVALID_INPUT_VALUE)
    }
}
