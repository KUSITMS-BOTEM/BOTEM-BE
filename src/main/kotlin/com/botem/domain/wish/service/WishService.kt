package com.botem.domain.wish.service

import com.botem.domain.funding.entity.FundingStatus
import com.botem.domain.funding.repository.FundingRepository
import com.botem.domain.product.service.RecommendedProductService
import com.botem.domain.wish.dto.response.WishPageResponse
import com.botem.domain.wish.dto.response.WishResponse
import com.botem.domain.wish.entity.Wish
import com.botem.domain.wish.entity.WishStatus
import com.botem.domain.wish.exception.WishErrorCode
import com.botem.domain.wish.repository.WishRepository
import com.botem.global.exception.BusinessException
import com.botem.global.exception.CommonErrorCode
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class WishService(
    private val wishRepository: WishRepository,
    private val fundingRepository: FundingRepository,
    private val recommendedProductService: RecommendedProductService,
) {

    // 운영자가 나중에 상품 정보를 바꿔도 담은 시점의 위시는 그대로 두도록 상품 정보를 복사해 저장
    @Transactional
    fun addRecommendedProduct(userId: Long, recommendedProductId: Long): WishResponse {
        val product = recommendedProductService.getOnSaleProduct(recommendedProductId)
        if (wishRepository.existsByUserIdAndRecommendedProductId(userId, recommendedProductId)) {
            throw BusinessException(WishErrorCode.WISH_ALREADY_ADDED)
        }

        val wish = Wish(
            userId = userId,
            productName = product.name,
            productImageUrl = product.imageUrl,
            price = product.price,
            productUrl = product.productUrl,
            sellerName = product.sellerName,
            recommendedProductId = product.id,
        )

        // 동시에 두 번 눌러 중복 확인을 함께 통과해도 유니크 제약으로 한 건만 저장
        val savedWish = try {
            wishRepository.saveAndFlush(wish)
        } catch (exception: DataIntegrityViolationException) {
            throw BusinessException(WishErrorCode.WISH_ALREADY_ADDED)
        }

        return WishResponse.from(savedWish)
    }

    @Transactional(readOnly = true)
    fun getWishes(userId: Long, page: Int, size: Int): WishPageResponse {
        validatePageRequest(page, size)

        val wishes = wishRepository.findAllByUserId(userId, PageRequest.of(page, size, WISH_SORT))

        return WishPageResponse.of(wishes, toResponses(userId, wishes.content))
    }

    @Transactional(readOnly = true)
    fun getWish(userId: Long, wishId: Long): WishResponse =
        toResponses(userId, listOf(findOwnWish(userId, wishId))).single()

    // 펀딩이 끝난 뒤에는 삭제해도 펀딩의 상품 스냅샷이 남아 있어 source_wish_id만 비워짐
    // TODO: 확인 후 삭제라 펀딩 개설과 동시에 일어나면 위시 연결이 끊긴 펀딩이 생길 수 있음. 펀딩 개설 API에서 위시를 비관적 락으로 조회해 막을 것
    @Transactional
    fun deleteWish(userId: Long, wishId: Long) {
        val wish = findOwnWish(userId, wishId)
        if (fundingRepository.existsByOwnerIdAndProductSourceWishIdAndStatusNotIn(userId, wishId, ENDED_FUNDING_STATUSES)) {
            throw BusinessException(WishErrorCode.WISH_IN_FUNDING)
        }

        wishRepository.delete(wish)
    }

    // 친구가 내 위시로 연 서프라이즈 펀딩이 드러나지 않도록 내가 연 펀딩만 상태에 반영
    private fun toResponses(userId: Long, wishes: List<Wish>): List<WishResponse> {
        if (wishes.isEmpty()) return emptyList()

        val fundingIdByWishId = fundingRepository
            .findAllByOwnerIdAndProductSourceWishIdInAndStatusNotIn(userId, wishes.map { it.id }, ENDED_FUNDING_STATUSES)
            .associate { it.product.sourceWishId to it.id }
        val discontinuedProductIds = recommendedProductService
            .findDiscontinuedProductIds(wishes.mapNotNull { it.recommendedProductId })

        return wishes.map { wish ->
            val fundingId = fundingIdByWishId[wish.id]
            val status = when {
                fundingId != null -> WishStatus.FUNDING
                wish.recommendedProductId in discontinuedProductIds -> WishStatus.DISCONTINUED
                else -> WishStatus.AVAILABLE
            }

            WishResponse.from(wish, status, fundingId)
        }
    }

    // 다른 회원의 위시는 존재 여부를 드러내지 않도록 없는 위시와 같은 오류로 응답
    private fun findOwnWish(userId: Long, wishId: Long): Wish =
        wishRepository.findByIdAndUserId(wishId, userId)
            ?: throw BusinessException(WishErrorCode.WISH_NOT_FOUND)

    private fun validatePageRequest(page: Int, size: Int) {
        if (page < 0 || size !in 1..MAX_PAGE_SIZE) {
            throw BusinessException(
                CommonErrorCode.INVALID_INPUT_VALUE,
                "page는 0 이상, size는 1 이상 ${MAX_PAGE_SIZE} 이하여야 합니다.",
            )
        }
    }

    companion object {
        private const val MAX_PAGE_SIZE = 20
        private val ENDED_FUNDING_STATUSES = setOf(FundingStatus.COMPLETED, FundingStatus.CANCELLED)
        private val WISH_SORT = Sort.by(
            Sort.Order.desc("createdAt"),
            Sort.Order.desc("id"),
        )
    }
}
