package com.botem.domain.auth.client

import com.botem.global.exception.AuthErrorCode
import com.botem.global.exception.BusinessException
import com.botem.global.property.KakaoProperties
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.web.client.RestClient

class KakaoOAuthClientRedirectUriTest {

    private val client = KakaoOAuthClient(
        KakaoProperties(
            clientId = "client-id",
            clientSecret = "client-secret",
            redirectUri = "https://default.example/callback",
            allowedRedirectUris = listOf("https://botem.me/auth/callback"),
        ),
        RestClient.builder(),
    )

    @Test
    fun `허용된 redirect URI로 로그인 URI를 만든다`() {
        assertThat(client.getLoginUri("https://botem.me/auth/callback"))
            .contains("redirect_uri=https://botem.me/auth/callback")
    }

    @Test
    fun `redirect URI를 생략하면 기본값을 사용한다`() {
        assertThat(client.getLoginUri(null)).contains("redirect_uri=https://default.example/callback")
    }

    @Test
    fun `필수 동의 항목과 친구 목록 제공 동의를 함께 요청한다`() {
        assertThat(client.getLoginUri(null))
            .contains("scope=profile_nickname,profile_image,account_email,friends")
    }

    @Test
    fun `허용 목록에 없는 redirect URI는 거부한다`() {
        assertThatThrownBy { client.getLoginUri("https://botem.me/auth/callback/evil") }
            .isInstanceOf(BusinessException::class.java)
            .extracting("errorCode")
            .isEqualTo(AuthErrorCode.INVALID_REDIRECT_URI)
    }

    @Test
    fun `로그인 요청도 허용 목록에 없는 redirect URI는 카카오 호출 전에 거부한다`() {
        assertThatThrownBy { client.login("code", "https://evil.example/callback") }
            .isInstanceOf(BusinessException::class.java)
            .extracting("errorCode")
            .isEqualTo(AuthErrorCode.INVALID_REDIRECT_URI)
    }
}
