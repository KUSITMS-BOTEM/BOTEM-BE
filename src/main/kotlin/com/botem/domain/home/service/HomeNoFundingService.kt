package com.botem.domain.home.service

import com.botem.domain.home.dto.response.HomeNoFundingResponse
import com.botem.domain.home.dto.response.HomeNoFundingState
import com.botem.domain.home.dto.response.HomeWishPageResponse
import com.botem.domain.home.exception.HomeErrorCode
import com.botem.domain.user.entity.UserStatus
import com.botem.domain.user.entity.daysUntilNextBirthday
import com.botem.domain.user.exception.UserErrorCode
import com.botem.domain.user.repository.UserRepository
import com.botem.domain.wish.repository.WishRepository
import com.botem.global.exception.AuthErrorCode
import com.botem.global.exception.BusinessException
import com.botem.global.exception.CommonErrorCode
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate
import java.time.MonthDay

@Service
class HomeNoFundingService(
    private val homeFundingService: HomeFundingService,
    private val userRepository: UserRepository,
    private val wishRepository: WishRepository,
    private val clock: Clock,
) {

    @Transactional(readOnly = true)
    fun getNoFundingHome(
        userId: Long,
        page: Int = 0,
        size: Int = DEFAULT_WISH_PAGE_SIZE,
    ): HomeNoFundingResponse {
        validatePageRequest(page, size)

        val homeFundings = homeFundingService.getHomeFundings(userId)
        if (homeFundings.fundingCards.isNotEmpty()) {
            throw BusinessException(HomeErrorCode.FUNDING_CARD_EXISTS)
        }

        val user = userRepository.findById(userId)
            .orElseThrow { BusinessException(UserErrorCode.USER_NOT_FOUND) }
        if (user.status != UserStatus.ACTIVE) {
            throw BusinessException(AuthErrorCode.WITHDRAWN_USER)
        }

        val wishes = wishRepository.findAllByUserId(
            userId = userId,
            pageable = PageRequest.of(page, size, WISH_SORT),
        )

        return HomeNoFundingResponse(
            state = if (wishes.totalElements == 0L) HomeNoFundingState.EMPTY else HomeNoFundingState.WISH_RECOMMENDATION,
            nickname = user.name ?: user.nickname,
            birthdayDday = calculateBirthdayDday(user.birthday, LocalDate.now(clock)),
            wishes = HomeWishPageResponse.from(wishes),
        )
    }

    private fun validatePageRequest(page: Int, size: Int) {
        if (page < 0 || size !in 1..MAX_WISH_PAGE_SIZE) {
            throw BusinessException(
                CommonErrorCode.INVALID_INPUT_VALUE,
                "page는 0 이상, size는 1 이상 ${MAX_WISH_PAGE_SIZE} 이하여야 합니다.",
            )
        }
    }

    private fun calculateBirthdayDday(
        birthday: MonthDay?,
        today: LocalDate,
    ): Long? {
        birthday ?: return null

        return birthday.daysUntilNextBirthday(today)
            .takeIf { it <= BIRTHDAY_DDAY_EXPOSURE_DAYS }
    }

    companion object {
        private const val BIRTHDAY_DDAY_EXPOSURE_DAYS = 30L
        private const val DEFAULT_WISH_PAGE_SIZE = 10
        private const val MAX_WISH_PAGE_SIZE = 20
        private val WISH_SORT = Sort.by(
            Sort.Order.desc("createdAt"),
            Sort.Order.desc("id"),
        )
    }
}
