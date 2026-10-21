package com.botem.domain.auth.service

import com.botem.domain.auth.client.KakaoOAuthClient
import com.botem.domain.auth.dto.KakaoLoginUriResponse
import com.botem.domain.auth.dto.TokenResponse
import com.botem.domain.auth.entity.RefreshToken
import com.botem.domain.auth.repository.RefreshTokenRepository
import com.botem.domain.user.entity.User
import com.botem.domain.user.repository.UserRepository
import com.botem.global.exception.AuthErrorCode
import com.botem.global.exception.BusinessException
import com.botem.global.security.JwtProvider
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AuthService(
    private val kakaoOAuthClient: KakaoOAuthClient,
    private val userRepository: UserRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val jwtProvider: JwtProvider,
) {

    fun getKakaoLoginUri(): KakaoLoginUriResponse = KakaoLoginUriResponse(kakaoOAuthClient.getLoginUri())

    @Transactional
    fun kakaoLogin(code: String): TokenResponse {
        val userInfo = kakaoOAuthClient.getUserInfo(code)
        val profile = userInfo.kakaoAccount?.profile
        val nickname = profile?.nickname
            ?: throw BusinessException(AuthErrorCode.KAKAO_LOGIN_FAILED, "닉네임 동의가 필요합니다.")

        val user = userRepository.findByKakaoId(userInfo.id)
            ?: userRepository.save(
                User(
                    kakaoId = userInfo.id,
                    nickname = nickname,
                    profileImageUrl = profile.profileImageUrl,
                ),
            )

        return issueTokens(user.id)
    }

    @Transactional
    fun reissue(refreshToken: String): TokenResponse {
        val userId = jwtProvider.getUserIdFromRefreshToken(refreshToken)
        val savedRefreshToken = refreshTokenRepository.findByUserId(userId)

        if (savedRefreshToken?.token != refreshToken) {
            throw BusinessException(AuthErrorCode.INVALID_TOKEN)
        }

        return issueTokens(userId)
    }

    private fun issueTokens(userId: Long): TokenResponse {
        val accessToken = jwtProvider.createAccessToken(userId)
        val refreshToken = jwtProvider.createRefreshToken(userId)

        refreshTokenRepository.findByUserId(userId)?.rotate(refreshToken)
            ?: refreshTokenRepository.save(RefreshToken(userId = userId, token = refreshToken))

        return TokenResponse(accessToken = accessToken, refreshToken = refreshToken)
    }
}
