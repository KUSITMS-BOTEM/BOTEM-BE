package com.botem.domain.user.service

import com.botem.domain.user.dto.request.UpdateMyProfileRequest
import com.botem.domain.user.dto.response.MyProfileResponse
import com.botem.domain.user.entity.User
import com.botem.domain.user.exception.UserErrorCode
import com.botem.domain.user.repository.UserRepository
import com.botem.global.exception.BusinessException
import com.botem.global.exception.CommonErrorCode
import com.botem.global.property.WishProfileProperties
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserService(
    private val userRepository: UserRepository,
    private val wishProfileProperties: WishProfileProperties,
) {

    @Transactional(readOnly = true)
    fun getMyProfile(userId: Long): MyProfileResponse =
        MyProfileResponse.from(findUser(userId), wishProfileProperties.baseUrl)

    @Transactional
    fun updateMyProfile(userId: Long, request: UpdateMyProfileRequest): MyProfileResponse {
        val user = findUser(userId)
        val nickname = request.nickname.trim { it.isWhitespace() || Character.isSpaceChar(it) }

        if (nickname.isEmpty()) {
            throw BusinessException(CommonErrorCode.INVALID_INPUT_VALUE, "nickname: 공백일 수 없습니다")
        }

        user.updateProfile(
            nickname = nickname,
            birthday = request.birthday,
        )

        return MyProfileResponse.from(user, wishProfileProperties.baseUrl)
    }

    private fun findUser(userId: Long): User =
        userRepository.findById(userId)
            .orElseThrow { BusinessException(UserErrorCode.USER_NOT_FOUND) }
}
