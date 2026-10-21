package com.botem.domain.auth.client

import com.botem.global.config.KakaoProperties
import com.botem.global.exception.AuthErrorCode
import com.botem.global.exception.BusinessException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.body
import org.springframework.web.util.UriComponentsBuilder

@Component
class KakaoOAuthClient(
    private val kakaoProperties: KakaoProperties,
    restClientBuilder: RestClient.Builder,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    private val restClient = restClientBuilder.build()

    fun getLoginUri(): String =
        UriComponentsBuilder.fromUriString(AUTHORIZE_URI)
            .queryParam("client_id", kakaoProperties.clientId)
            .queryParam("redirect_uri", kakaoProperties.redirectUri)
            .queryParam("response_type", "code")
            .encode()
            .toUriString()

    fun getUserInfo(code: String): KakaoUserInfoResponse {
        try {
            val accessToken = requestAccessToken(code)

            return restClient.get()
                .uri(USER_INFO_URI)
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .retrieve()
                .body<KakaoUserInfoResponse>()
                ?: throw BusinessException(AuthErrorCode.KAKAO_LOGIN_FAILED)
        } catch (exception: RestClientException) {
            log.warn("Kakao login request failed", exception)

            throw BusinessException(AuthErrorCode.KAKAO_LOGIN_FAILED)
        }
    }

    private fun requestAccessToken(code: String): String {
        val form = LinkedMultiValueMap<String, String>().apply {
            add("grant_type", "authorization_code")
            add("client_id", kakaoProperties.clientId)
            add("client_secret", kakaoProperties.clientSecret)
            add("redirect_uri", kakaoProperties.redirectUri)
            add("code", code)
        }

        return restClient.post()
            .uri(TOKEN_URI)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(form)
            .retrieve()
            .body<KakaoTokenResponse>()
            ?.accessToken
            ?: throw BusinessException(AuthErrorCode.KAKAO_LOGIN_FAILED)
    }

    companion object {
        private const val AUTHORIZE_URI = "https://kauth.kakao.com/oauth/authorize"
        private const val TOKEN_URI = "https://kauth.kakao.com/oauth/token"
        private const val USER_INFO_URI = "https://kapi.kakao.com/v2/user/me"
    }
}
