package com.botem.global.monitoring

import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

// 수집 에이전트는 토큰 없이 지표를 가져간다. 외부 접근은 Nginx와 127.0.0.1 바인딩으로 막는다
@SpringBootTest(properties = ["management.prometheus.metrics.export.enabled=true"])
@AutoConfigureMockMvc
class PrometheusEndpointTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `인증 없이 API 응답 시간·DB 커넥션·JVM 지표를 Prometheus 형식으로 내보낸다`() {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk)

        mockMvc.perform(get("/actuator/prometheus"))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("application=\"botem\"")))
            // 지표 개수를 줄이려고 지정한 고정 구간(0.3초)만 남긴다
            .andExpect(content().string(containsString("http_server_requests_seconds_bucket")))
            .andExpect(content().string(containsString("le=\"0.3\"")))
            .andExpect(content().string(containsString("hikaricp_connections_active")))
            .andExpect(content().string(containsString("jvm_memory_used_bytes")))
    }

    @Test
    fun `노출하지 않은 actuator 엔드포인트는 열지 않는다`() {
        mockMvc.perform(get("/actuator/env")).andExpect(status().is4xxClientError)
        mockMvc.perform(get("/actuator/beans")).andExpect(status().is4xxClientError)
    }
}
