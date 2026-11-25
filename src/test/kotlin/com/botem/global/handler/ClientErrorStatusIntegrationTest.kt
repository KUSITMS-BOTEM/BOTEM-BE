package com.botem.global.handler

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

// 로그인한 사용자의 잘못된 요청이 500(서버 오류)이 아니라 원래 상태 코드로 응답되는지 확인한다
@SpringBootTest
@AutoConfigureMockMvc
@Import(ClientErrorStatusIntegrationTest.RequiredParamController::class)
class ClientErrorStatusIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    private val loggedIn = authentication(UsernamePasswordAuthenticationToken(1L, null, emptyList()))

    @Test
    fun `없는 주소는 404로 응답한다`() {
        mockMvc.perform(get("/api/not-exists").with(loggedIn))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("COMMON_404"))
    }

    @Test
    fun `지원하지 않는 메서드는 허용 메서드를 알려 주며 405로 응답한다`() {
        mockMvc.perform(delete("/api/home/fundings").with(loggedIn))
            .andExpect(status().isMethodNotAllowed)
            .andExpect(header().string(HttpHeaders.ALLOW, "GET"))
            .andExpect(jsonPath("$.error.code").value("COMMON_405"))
    }

    @Test
    fun `지원하지 않는 요청 형식은 415로 응답한다`() {
        mockMvc.perform(post("/api/wishes").with(loggedIn).contentType(MediaType.TEXT_PLAIN).content("text"))
            .andExpect(status().isUnsupportedMediaType)
            .andExpect(jsonPath("$.error.code").value("COMMON_415"))
    }

    @Test
    fun `필수 쿼리 파라미터가 빠지면 400으로 응답한다`() {
        // 입력 본문·타입 오류 전용 처리기가 아니라, 상태 코드를 가진 Spring 요청 오류의 기본 분기를 거친다
        mockMvc.perform(get(REQUIRED_PARAM_PATH).with(loggedIn))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("COMMON_001"))
    }

    @Test
    fun `비로그인 요청은 지금처럼 인증 단계에서 401로 막는다`() {
        mockMvc.perform(get("/api/not-exists"))
            .andExpect(status().isUnauthorized)
    }

    // 운영 API에는 필수 쿼리 파라미터가 없어서 이 테스트에서만 쓰는 엔드포인트. 테스트 클래스 안에 있어 다른 테스트에는 등록되지 않는다
    @RestController
    class RequiredParamController {

        @GetMapping(REQUIRED_PARAM_PATH)
        fun requiredParam(@RequestParam keyword: String): String = keyword
    }

    companion object {
        private const val REQUIRED_PARAM_PATH = "/api/test/required-param"
    }
}
