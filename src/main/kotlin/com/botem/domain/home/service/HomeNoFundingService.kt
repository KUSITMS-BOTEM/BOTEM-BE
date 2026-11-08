package com.botem.domain.home.service

import com.botem.domain.home.dto.response.HomeNoFundingResponse
import com.botem.domain.home.dto.response.HomeNoFundingState
import com.botem.domain.home.dto.response.HomeWishCardResponse
import com.botem.domain.home.exception.HomeErrorCode
import com.botem.domain.user.entity.UserStatus
import com.botem.domain.user.exception.UserErrorCode
import com.botem.domain.user.repository.UserRepository
import com.botem.domain.wish.repository.WishRepository
import com.botem.global.exception.AuthErrorCode
import com.botem.global.exception.BusinessException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate
import java.time.MonthDay
import java.time.temporal.ChronoUnit

@Service
class HomeNoFundingService(
    private val homeFundingService: HomeFundingService,
    private val userRepository: UserRepository,
    private val wishRepository: WishRepository,
    private val clock: Clock,
) {

    @Transactional(readOnly = true)
    fun getNoFundingHome(userId: Long): HomeNoFundingResponse {
        val homeFundings = homeFundingService.getHomeFundings(userId)
        if (homeFundings.fundingCards.isNotEmpty()) {
            throw BusinessException(HomeErrorCode.FUNDING_CARD_EXISTS)
        }

        val user = userRepository.findById(userId)
            .orElseThrow { BusinessException(UserErrorCode.USER_NOT_FOUND) }
        if (user.status != UserStatus.ACTIVE) {
            throw BusinessException(AuthErrorCode.WITHDRAWN_USER)
        }

        val wishes = wishRepository.findAllByUserIdOrderByCreatedAtDesc(userId)
            .map(HomeWishCardResponse::from)

        return HomeNoFundingResponse(
            state = if (wishes.isEmpty()) HomeNoFundingState.EMPTY else HomeNoFundingState.WISH_RECOMMENDATION,
            nickname = user.nickname,
            birthdayDday = calculateBirthdayDday(user.birthday, LocalDate.now(clock)),
            wishes = wishes,
        )
    }

    private fun calculateBirthdayDday(
        birthday: MonthDay?,
        today: LocalDate,
    ): Long? {
        birthday ?: return null

        var nextBirthday = birthday.atYear(today.year)
        if (nextBirthday.isBefore(today)) {
            nextBirthday = birthday.atYear(today.year + 1)
        }

        return ChronoUnit.DAYS.between(today, nextBirthday)
            .takeIf { it <= BIRTHDAY_DDAY_EXPOSURE_DAYS }
    }

    companion object {
        private const val BIRTHDAY_DDAY_EXPOSURE_DAYS = 30L
    }
}
