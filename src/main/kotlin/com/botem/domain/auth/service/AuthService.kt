package com.botem.domain.auth.service

import com.botem.domain.auth.client.KakaoOAuthClient
import com.botem.domain.auth.dto.response.KakaoLoginUriResponse
import com.botem.domain.auth.dto.response.TokenResponse
import com.botem.domain.auth.entity.RefreshToken
import com.botem.domain.auth.repository.RefreshTokenRepository
import com.botem.domain.user.entity.User
import com.botem.domain.user.repository.UserRepository
import com.botem.global.exception.AuthErrorCode
import com.botem.global.exception.BusinessException
import com.botem.global.jwt.JwtProvider
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.security.MessageDigest
import java.util.HexFormat

@Service
class AuthService(
    private val kakaoOAuthClient: KakaoOAuthClient,
    private val userRepository: UserRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val jwtProvider: JwtProvider,
    private val transactionTemplate: TransactionTemplate,
) {

    fun getKakaoLoginUri(): KakaoLoginUriResponse = KakaoLoginUriResponse(kakaoOAuthClient.getLoginUri())

    // 카카오 API 호출 동안 DB 커넥션을 잡지 않도록 회원 조회·가입과 토큰 저장만 트랜잭션으로 묶음
    fun kakaoLogin(code: String): TokenResponse {
        val userInfo = kakaoOAuthClient.getUserInfo(code)
        val profile = userInfo.kakaoAccount?.profile
        val nickname = profile?.nickname
            ?: throw BusinessException(AuthErrorCode.KAKAO_LOGIN_FAILED, "닉네임 동의가 필요합니다.")

        return transactionTemplate.execute {
            val user = userRepository.findByKakaoId(userInfo.id)
                ?: userRepository.save(
                    User(
                        kakaoId = userInfo.id,
                        nickname = nickname,
                        profileImageUrl = profile.profileImageUrl,
                    ),
                )

            issueTokens(user.id)
        }
    }

    @Transactional
    fun reissue(refreshToken: String): TokenResponse {
        val userId = jwtProvider.getUserIdFromRefreshToken(refreshToken)
        val savedRefreshToken = refreshTokenRepository.findByUserId(userId)

        if (savedRefreshToken?.tokenHash != hash(refreshToken)) {
            throw BusinessException(AuthErrorCode.INVALID_TOKEN)
        }

        return issueTokens(userId)
    }

    private fun issueTokens(userId: Long): TokenResponse {
        val accessToken = jwtProvider.createAccessToken(userId)
        val refreshToken = jwtProvider.createRefreshToken(userId)

        val refreshTokenHash = hash(refreshToken)

        refreshTokenRepository.findByUserId(userId)?.rotate(refreshTokenHash)
            ?: refreshTokenRepository.save(RefreshToken(userId = userId, tokenHash = refreshTokenHash))

        return TokenResponse(accessToken = accessToken, refreshToken = refreshToken)
    }

    // DB가 유출돼도 Refresh 토큰을 재사용할 수 없도록 원문 대신 SHA-256 해시를 저장
    private fun hash(token: String): String =
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.toByteArray()))
}
