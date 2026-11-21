package com.botem.domain.user.service

import com.botem.domain.user.dto.request.UpdateMyProfileRequest
import com.botem.domain.user.dto.response.MyProfileResponse
import com.botem.domain.user.entity.User
import com.botem.domain.user.entity.UserStatus
import com.botem.domain.user.exception.UserErrorCode
import com.botem.domain.user.repository.UserRepository
import com.botem.global.exception.AuthErrorCode
import com.botem.global.exception.BusinessException
import com.botem.global.exception.CommonErrorCode
import com.botem.global.property.WishProfileProperties
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.MonthDay

@Service
class UserService(
    private val userRepository: UserRepository,
    private val wishProfileProperties: WishProfileProperties,
    private val profileImageService: ProfileImageService,
) {

    @Transactional
    fun getMyProfile(userId: Long): MyProfileResponse {
        val user = findActiveUserForUpdate(userId)

        return toProfileResponse(user)
    }

    @Transactional
    fun updateMyProfile(userId: Long, request: UpdateMyProfileRequest): MyProfileResponse {
        val user = findActiveUserForUpdate(userId)

        updateProfile(user, request.name, request.birthday, request.profileImageUrl)

        return toProfileResponse(user)
    }

    @Transactional
    fun updateOnboardingProfile(userId: Long, name: String, birthday: MonthDay?, profileImageUrl: String?) {
        val user = findActiveUserForUpdate(userId)

        updateProfile(user, name, birthday, profileImageUrl)
    }

    // 온보딩과 내 프로필 수정이 같은 이름 정규화·이미지 URL 검증을 따르도록 한 곳에서 처리
    private fun updateProfile(user: User, name: String, birthday: MonthDay?, profileImageUrl: String?) {
        val normalizedName = name.trim { it.isWhitespace() || Character.isSpaceChar(it) }

        if (normalizedName.isEmpty()) {
            throw BusinessException(CommonErrorCode.INVALID_INPUT_VALUE, "name: 공백일 수 없습니다")
        }
        if (normalizedName.length > MAX_NAME_LENGTH) {
            throw BusinessException(CommonErrorCode.INVALID_INPUT_VALUE, "name: ${MAX_NAME_LENGTH}자 이하여야 합니다")
        }

        profileImageUrl?.let { profileImageService.validateImageUrl(user.id, it) }

        user.updateProfile(name = normalizedName, birthday = birthday, profileImageUrl = profileImageUrl)
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

    // 다른 도메인이 회원 행을 잠글 때 쓴다. 잠금은 호출한 쪽 트랜잭션이 끝날 때까지 유지돼야 하므로 트랜잭션 안에서만 호출할 수 있다
    @Transactional(propagation = Propagation.MANDATORY)
    fun lockUser(userId: Long): User? =
        userRepository.findByIdForUpdate(userId)

    // 회원 행을 잠그고 활성 회원인지 확인한다. 없으면 USER_NOT_FOUND, 탈퇴했으면 WITHDRAWN_USER
    @Transactional(propagation = Propagation.MANDATORY)
    fun lockActiveUser(userId: Long): User =
        findActiveUserForUpdate(userId)

    @Transactional(propagation = Propagation.MANDATORY)
    fun createUser(user: User): User =
        userRepository.save(user)

    private fun findActiveUserForUpdate(userId: Long): User {
        val user = userRepository.findByIdForUpdate(userId)
            ?: throw BusinessException(UserErrorCode.USER_NOT_FOUND)

        if (user.status != UserStatus.ACTIVE) {
            throw BusinessException(AuthErrorCode.WITHDRAWN_USER)
        }

        return user
    }

    companion object {
        private const val MAX_NAME_LENGTH = 50
    }
}
