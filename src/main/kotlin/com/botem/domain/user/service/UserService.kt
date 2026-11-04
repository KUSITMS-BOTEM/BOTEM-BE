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

    @Transactional
    fun getMyProfile(userId: Long): MyProfileResponse {
        val user = findUser(userId)

        return toProfileResponse(user)
    }

    @Transactional
    fun updateMyProfile(userId: Long, request: UpdateMyProfileRequest): MyProfileResponse {
        val user = findUser(userId)
        val nickname = request.nickname.trim { it.isWhitespace() || Character.isSpaceChar(it) }

        if (nickname.isEmpty()) {
            throw BusinessException(CommonErrorCode.INVALID_INPUT_VALUE, "nickname: 공백일 수 없습니다")
        }
        if (nickname.length > MAX_NICKNAME_LENGTH) {
            throw BusinessException(
                CommonErrorCode.INVALID_INPUT_VALUE,
                "nickname: ${MAX_NICKNAME_LENGTH}자 이하여야 합니다",
            )
        }

        user.updateProfile(
            nickname = nickname,
            birthday = request.birthday,
        )

        return toProfileResponse(user)
    }

    private fun toProfileResponse(user: User): MyProfileResponse =
        MyProfileResponse.from(
            user = user,
            wishProfileBaseUrl = wishProfileProperties.baseUrl,
            wishProfileKey = getOrCreateWishProfileKey(user),
        )

    private fun getOrCreateWishProfileKey(user: User): String {
        user.wishProfileKey?.let { return it }

        val newKey = User.generateWishProfileKey()
        val updated = userRepository.updateWishProfileKeyIfNull(user.id, newKey)

        if (updated == 1) {
            return newKey
        }

        return userRepository.findWishProfileKeyForUpdate(user.id)
            ?: throw BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR)
    }

    private fun findUser(userId: Long): User =
        userRepository.findById(userId)
            .orElseThrow { BusinessException(UserErrorCode.USER_NOT_FOUND) }

    companion object {
        private const val MAX_NICKNAME_LENGTH = 255
    }
}
