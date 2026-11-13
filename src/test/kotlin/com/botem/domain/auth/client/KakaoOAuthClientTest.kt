package com.botem.domain.auth.client

import com.botem.global.exception.AuthErrorCode
import com.botem.global.exception.BusinessException
import com.botem.global.property.KakaoProperties
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class KakaoOAuthClientTest {

    private val restClientBuilder = RestClient.builder()

    // 클라이언트가 builder로 RestClient를 만들기 전에 가짜 서버를 연결해야 카카오 호출을 가로챈다
    private val kakaoServer = MockRestServiceServer.bindTo(restClientBuilder).build()

    private val client = KakaoOAuthClient(
        KakaoProperties(
            clientId = "client-id",
            clientSecret = "client-secret",
            redirectUri = REDIRECT_URI,
            allowedRedirectUris = emptyList(),
        ),
        restClientBuilder,
    )

    @Test
    fun `인가 코드로 토큰을 발급받아 카카오 회원 정보를 조회한다`() {
        kakaoServer.expect(requestTo(TOKEN_URI))
            .andExpect(method(HttpMethod.POST))
            .andExpect(
                content().formData(
                    LinkedMultiValueMap<String, String>().apply {
                        add("grant_type", "authorization_code")
                        add("client_id", "client-id")
                        add("client_secret", "client-secret")
                        add("redirect_uri", REDIRECT_URI)
                        add("code", "auth-code")
                    },
                ),
            )
            .andRespond(withSuccess("""{ "access_token": "kakao-token" }""", MediaType.APPLICATION_JSON))
        kakaoServer.expect(requestTo(USER_INFO_URI))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer kakao-token"))
            .andRespond(
                withSuccess(
                    """
                    {
                      "id": 12345,
                      "kakao_account": {
                        "profile": { "nickname": "지유", "profile_image_url": "https://k.kakaocdn.net/jiyu.jpg" },
                        "email": "jiyu@kakao.com",
                        "is_email_valid": true,
                        "is_email_verified": true
                      }
                    }
                    """,
                    MediaType.APPLICATION_JSON,
                ),
            )

        val userInfo = client.getUserInfo("auth-code", null)

        kakaoServer.verify()
        assertEquals(12345L, userInfo.id)
        assertEquals("지유", userInfo.kakaoAccount?.profile?.nickname)
        assertEquals("https://k.kakaocdn.net/jiyu.jpg", userInfo.kakaoAccount?.profile?.profileImageUrl)
        assertEquals("jiyu@kakao.com", userInfo.kakaoAccount?.verifiedEmail())
    }

    @Test
    fun `토큰 발급에 실패하면 회원 정보를 조회하지 않고 카카오 로그인 실패로 응답한다`() {
        kakaoServer.expect(requestTo(TOKEN_URI)).andRespond(withStatus(HttpStatus.UNAUTHORIZED))

        val exception = assertFailsWith<BusinessException> { client.getUserInfo("expired-code", null) }

        kakaoServer.verify()
        assertEquals(AuthErrorCode.KAKAO_LOGIN_FAILED, exception.errorCode)
    }

    @Test
    fun `회원 정보 조회에 실패하면 카카오 로그인 실패로 응답한다`() {
        kakaoServer.expect(requestTo(TOKEN_URI))
            .andRespond(withSuccess("""{ "access_token": "kakao-token" }""", MediaType.APPLICATION_JSON))
        kakaoServer.expect(requestTo(USER_INFO_URI)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR))

        val exception = assertFailsWith<BusinessException> { client.getUserInfo("auth-code", null) }

        kakaoServer.verify()
        assertEquals(AuthErrorCode.KAKAO_LOGIN_FAILED, exception.errorCode)
    }

    companion object {
        private const val REDIRECT_URI = "https://botem.site/auth/callback"
        private const val TOKEN_URI = "https://kauth.kakao.com/oauth/token"
        private const val USER_INFO_URI = "https://kapi.kakao.com/v2/user/me?secure_resource=true"
    }
}
