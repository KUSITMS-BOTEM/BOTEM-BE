package com.botem.domain.user.service

import com.botem.domain.user.dto.request.UpdateMyProfileRequest
import com.botem.domain.user.dto.response.MyProfileResponse
import com.botem.domain.user.entity.User
import com.botem.domain.user.exception.UserErrorCode
import com.botem.domain.user.repository.UserRepository
import com.botem.global.exception.BusinessException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserService(
    private val userRepository: UserRepository,
) {

    @Transactional(readOnly = true)
    fun getMyProfile(userId: Long): MyProfileResponse =
        MyProfileResponse.from(findUser(userId))

    @Transactional
    fun updateMyProfile(userId: Long, request: UpdateMyProfileRequest): MyProfileResponse {
        val user = findUser(userId)

        user.updateProfile(
            nickname = request.nickname.trim(),
            birthday = request.birthday,
        )

        return MyProfileResponse.from(user)
    }

    private fun findUser(userId: Long): User =
        userRepository.findById(userId)
            .orElseThrow { BusinessException(UserErrorCode.USER_NOT_FOUND) }
}
