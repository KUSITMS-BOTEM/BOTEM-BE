package com.botem.domain.auth.service

import com.botem.domain.auth.repository.RefreshTokenRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

// 다른 도메인이 리프레시 토큰을 다룰 때 쓰는 진입점. 로그인·재발급 흐름은 AuthService가 맡는다
@Service
class RefreshTokenService(
    private val refreshTokenRepository: RefreshTokenRepository,
) {

    // 회원 탈퇴 트랜잭션 안에서 회원 상태 변경과 함께 삭제한다
    @Transactional(propagation = Propagation.MANDATORY)
    fun deleteAllOf(userId: Long) {
        refreshTokenRepository.deleteByUserId(userId)
    }
}
