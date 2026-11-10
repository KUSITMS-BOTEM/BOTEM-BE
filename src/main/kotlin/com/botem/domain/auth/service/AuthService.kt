package com.botem.domain.auth.service

import com.botem.domain.account.service.PayoutAccountService
import com.botem.domain.auth.client.KakaoOAuthClient
import com.botem.domain.auth.dto.response.KakaoLoginUriResponse
import com.botem.domain.auth.entity.RefreshToken
import com.botem.domain.auth.repository.RefreshTokenRepository
import com.botem.domain.user.entity.User
import com.botem.domain.user.entity.UserStatus
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
    private val payoutAccountService: PayoutAccountService,
    private val jwtProvider: JwtProvider,
    private val transactionTemplate: TransactionTemplate,
) {

    fun getKakaoLoginUri(redirectUri: String?): KakaoLoginUriResponse =
        KakaoLoginUriResponse(kakaoOAuthClient.getLoginUri(redirectUri))

    // 카카오 API 호출 동안 DB 커넥션을 잡지 않도록 회원 조회·가입과 토큰 저장만 트랜잭션으로 묶음
    fun kakaoLogin(code: String, redirectUri: String?): KakaoLoginResult {
        val userInfo = kakaoOAuthClient.getUserInfo(code, redirectUri)
        val profile = userInfo.kakaoAccount?.profile
        val nickname = profile?.nickname
            ?: throw BusinessException(AuthErrorCode.KAKAO_LOGIN_FAILED, "닉네임 동의가 필요합니다.")
        val email = userInfo.kakaoAccount?.verifiedEmail()

        return transactionTemplate.execute {
            val existingUser = userRepository.findByKakaoId(userInfo.id)
            if (existingUser?.status == UserStatus.WITHDRAWN) {
                throw BusinessException(AuthErrorCode.WITHDRAWN_USER)
            }
            val user = existingUser
                ?: userRepository.save(
                    User(
                        kakaoId = userInfo.id,
                        nickname = nickname,
                        profileImageUrl = profile.profileImageUrl,
                        email = email,
                    ),
                )

            // 이메일은 필수 동의 항목이라 로그인마다 카카오 값으로 덮어써, 만료·미인증된 이메일이 남지 않게 함
            existingUser?.updateEmail(email)

            // 온보딩 마지막 단계인 받을 계좌 등록을 마치지 않았으면 신규 회원으로 보고 온보딩을 다시 진행
            val isNewUser = existingUser == null || !payoutAccountService.hasPayoutAccount(user.id)

            KakaoLoginResult(tokens = issueTokens(user.id), isNewUser = isNewUser)
        }
    }

    @Transactional
    fun reissue(refreshToken: String): IssuedTokens {
        val userId = jwtProvider.getUserIdFromRefreshToken(refreshToken)
        validateActiveUser(userId)
        val savedRefreshToken = refreshTokenRepository.findByUserId(userId)

        if (savedRefreshToken?.tokenHash != hash(refreshToken)) {
            throw BusinessException(AuthErrorCode.INVALID_TOKEN)
        }

        return issueTokens(userId)
    }

    private fun validateActiveUser(userId: Long) {
        when (userRepository.findStatusById(userId)) {
            UserStatus.ACTIVE -> Unit
            UserStatus.WITHDRAWN -> throw BusinessException(AuthErrorCode.WITHDRAWN_USER)
            null -> throw BusinessException(AuthErrorCode.INVALID_TOKEN)
        }
    }

    // 만료되거나 이미 교체된 토큰으로도 쿠키는 지울 수 있어야 하므로 예외를 던지지 않고, 현재 토큰일 때만 삭제
    @Transactional
    fun logout(refreshToken: String) {
        val userId = try {
            jwtProvider.getUserIdFromRefreshToken(refreshToken)
        } catch (exception: BusinessException) {
            return
        }

        val savedRefreshToken = refreshTokenRepository.findByUserId(userId) ?: return

        if (savedRefreshToken.tokenHash == hash(refreshToken)) {
            refreshTokenRepository.delete(savedRefreshToken)
        }
    }

    private fun issueTokens(userId: Long): IssuedTokens {
        val accessToken = jwtProvider.createAccessToken(userId)
        val refreshToken = jwtProvider.createRefreshToken(userId)

        val refreshTokenHash = hash(refreshToken)

        refreshTokenRepository.findByUserId(userId)?.rotate(refreshTokenHash)
            ?: refreshTokenRepository.save(RefreshToken(userId = userId, tokenHash = refreshTokenHash))

        return IssuedTokens(accessToken = accessToken, refreshToken = refreshToken)
    }

    // DB가 유출돼도 Refresh 토큰을 재사용할 수 없도록 원문 대신 SHA-256 해시를 저장
    private fun hash(token: String): String =
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.toByteArray()))
}
