package com.botem.domain.auth.client

import com.botem.global.property.KakaoProperties
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

    fun getLoginUri(redirectUri: String?): String =
        UriComponentsBuilder.fromUriString(AUTHORIZE_URI)
            .queryParam("client_id", kakaoProperties.clientId)
            .queryParam("redirect_uri", resolveRedirectUri(redirectUri))
            .queryParam("response_type", "code")
            // scope를 지정하면 지정한 항목만 동의 화면에 나오므로 필수 항목도 함께 넣는다. friends는 선택 동의 (#248)
            .queryParam("scope", LOGIN_SCOPES)
            .encode()
            .toUriString()

    fun login(code: String, redirectUri: String?): KakaoLoginProfile {
        val resolvedRedirectUri = resolveRedirectUri(redirectUri)

        val (accessToken, userInfo) = try {
            val accessToken = requestAccessToken(code, resolvedRedirectUri)

            accessToken to (
                restClient.get()
                    .uri(USER_INFO_URI)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                    .retrieve()
                    .body<KakaoUserInfoResponse>()
                    ?: throw BusinessException(AuthErrorCode.KAKAO_LOGIN_FAILED)
                )
        } catch (exception: RestClientException) {
            log.warn("Kakao login request failed", exception)

            throw BusinessException(AuthErrorCode.KAKAO_LOGIN_FAILED)
        }

        return KakaoLoginProfile(userInfo, requestFriendKakaoIds(accessToken))
    }

    // 친구 목록은 부가 기능이라 동의 거부·권한 없음·카카오 오류가 나도 로그인은 계속한다.
    // 동의하지 않은 사용자의 오류 코드는 문서에 없어 모든 실패를 '친구 없음'으로 본다
    private fun requestFriendKakaoIds(accessToken: String): List<Long> {
        val friendKakaoIds = mutableListOf<Long>()
        try {
            for (page in 0 until MAX_FRIEND_PAGES) {
                val response = restClient.get()
                    .uri(FRIENDS_URI, FRIENDS_PAGE_SIZE, page * FRIENDS_PAGE_SIZE)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                    .retrieve()
                    .body<KakaoFriendsResponse>()
                    ?: break
                response.elements.mapTo(friendKakaoIds) { it.id }
                if (response.afterUrl == null) {
                    break
                }
            }
        } catch (exception: RestClientException) {
            log.warn("Kakao friends request failed", exception)

            return emptyList()
        }

        return friendKakaoIds
    }

    private fun requestAccessToken(code: String, redirectUri: String): String {
        val form = LinkedMultiValueMap<String, String>().apply {
            add("grant_type", "authorization_code")
            add("client_id", kakaoProperties.clientId)
            add("client_secret", kakaoProperties.clientSecret)
            add("redirect_uri", redirectUri)
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

    // 카카오가 인가 코드를 임의 주소로 보내지 않도록 허용 목록과 정확히 일치하는 값만 사용
    private fun resolveRedirectUri(redirectUri: String?): String {
        if (redirectUri == null) return kakaoProperties.redirectUri
        if (redirectUri !in kakaoProperties.allowedRedirectUris) {
            throw BusinessException(AuthErrorCode.INVALID_REDIRECT_URI)
        }

        return redirectUri
    }

    companion object {
        private const val AUTHORIZE_URI = "https://kauth.kakao.com/oauth/authorize"
        private const val TOKEN_URI = "https://kauth.kakao.com/oauth/token"
        private const val USER_INFO_URI = "https://kapi.kakao.com/v2/user/me?secure_resource=true"
        private const val FRIENDS_URI = "https://kapi.kakao.com/v1/api/talk/friends?limit={limit}&offset={offset}"
        private const val LOGIN_SCOPES = "profile_nickname,profile_image,account_email,friends"
        private const val FRIENDS_PAGE_SIZE = 100

        // ponytail: 로그인마다 최대 5,000명까지만 연결한다. 친구가 더 많은 사용자가 생기면 비동기 동기화로 옮긴다
        private const val MAX_FRIEND_PAGES = 50
    }
}
