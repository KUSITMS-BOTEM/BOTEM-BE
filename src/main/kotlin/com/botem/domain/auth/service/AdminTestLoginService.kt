package com.botem.domain.auth.service

import com.botem.domain.auth.dto.response.AccessTokenResponse
import com.botem.domain.user.entity.UserStatus
import com.botem.domain.user.exception.UserErrorCode
import com.botem.domain.user.repository.UserRepository
import com.botem.global.exception.BusinessException
import com.botem.global.jwt.JwtProvider
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AdminTestLoginService(
    private val userRepository: UserRepository,
    private val jwtProvider: JwtProvider,
) {

    // 이메일은 users 테이블에서 유일 제약이 아니므로, 활성 회원이 정확히 한 명일 때만 발급한다.
    // 테스트 로그인은 운영자 API Key로 이미 인증된 요청에서만 호출한다.
    @Transactional(readOnly = true)
    fun issueAccessToken(email: String): AccessTokenResponse {
        val user = userRepository.findAllByEmailAndStatus(email, UserStatus.ACTIVE).singleOrNull()
            ?: throw BusinessException(UserErrorCode.USER_NOT_FOUND)

        return AccessTokenResponse(jwtProvider.createAccessToken(user.id))
    }
}
