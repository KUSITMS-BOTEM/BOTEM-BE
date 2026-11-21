package com.botem.domain.funding.service

import com.botem.domain.funding.dto.response.FundingProductChangeResponse
import com.botem.domain.funding.entity.FundingProductSnapshot
import com.botem.domain.funding.entity.FundingStatus
import com.botem.domain.funding.exception.FundingErrorCode
import com.botem.domain.funding.repository.FundingProductChangeHistoryRepository
import com.botem.domain.funding.repository.FundingRepository
import com.botem.domain.user.service.UserService
import com.botem.global.exception.BusinessException
import java.time.Clock
import java.time.LocalDateTime
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class FundingProductChangeService(
    private val fundingRepository: FundingRepository,
    private val productChangeHistoryRepository: FundingProductChangeHistoryRepository,
    private val userService: UserService,
    private val resourceResolver: FundingCreationResourceResolver,
    private val clock: Clock,
) {

    @Transactional
    fun change(
        userId: Long,
        fundingId: Long,
        wishId: Long,
    ): FundingProductChangeResponse {
        userService.lockActiveUser(userId)
        val funding = fundingRepository.findByIdForUpdate(fundingId)
            ?.takeIf { it.status != FundingStatus.CANCELLED }
            ?: throw BusinessException(FundingErrorCode.FUNDING_NOT_FOUND)
        if (funding.ownerId != userId) {
            throw BusinessException(FundingErrorCode.FUNDING_OWNER_ONLY)
        }

        val changedAt = LocalDateTime.now(clock)
        if (funding.product.sourceWishId == wishId) {
            funding.changeProduct(funding.product, changedAt)
            return FundingProductChangeResponse.from(funding)
        }

        val wish = resourceResolver.resolveWish(userId, wishId)
        val history = funding.changeProduct(
            newProduct = FundingProductSnapshot(
                sourceWishId = wish.id,
                name = wish.productName,
                imageUrl = wish.productImageUrl,
                price = wish.price,
                url = wish.productUrl,
                sellerName = wish.sellerName,
            ),
            changedAt = changedAt,
        )
        productChangeHistoryRepository.save(history)

        return FundingProductChangeResponse.from(funding)
    }

}
