package com.botem.domain.home.service

import com.botem.domain.funding.repository.FundingRepository
import com.botem.domain.home.dto.response.SurpriseRevealCardListResponse
import com.botem.domain.home.dto.response.SurpriseRevealCardResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

@Service
class HomeSurpriseRevealService(
    private val fundingRepository: FundingRepository,
    private val clock: Clock,
) {

    @Transactional(readOnly = true)
    fun getCards(userId: Long): SurpriseRevealCardListResponse {
        val cards = fundingRepository.findRevealedSurpriseCards(
            recipientId = userId,
            now = LocalDateTime.now(clock),
        ).map(SurpriseRevealCardResponse::from)

        return SurpriseRevealCardListResponse(cards)
    }
}
