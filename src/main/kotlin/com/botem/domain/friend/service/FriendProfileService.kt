package com.botem.domain.friend.service

import com.botem.domain.friend.dto.response.FriendProfileFundingResponse
import com.botem.domain.friend.dto.response.FriendProfileResponse
import com.botem.domain.friend.dto.response.FriendProfileWishResponse
import com.botem.domain.friend.dto.response.FriendWishResponse
import com.botem.domain.friend.exception.FriendErrorCode
import com.botem.domain.friend.repository.FriendshipRepository
import com.botem.domain.funding.entity.Funding
import com.botem.domain.funding.entity.FundingStatus
import com.botem.domain.funding.entity.FundingType
import com.botem.domain.funding.repository.ContributionRepository
import com.botem.domain.funding.repository.FundingRepository
import com.botem.domain.funding.repository.projection.FundingContributionSummary
import com.botem.domain.funding.service.progressPercentage
import com.botem.domain.user.entity.User
import com.botem.domain.user.entity.UserStatus
import com.botem.domain.user.entity.daysUntilNextBirthday
import com.botem.domain.user.repository.UserRepository
import com.botem.domain.wish.entity.Wish
import com.botem.domain.wish.exception.WishErrorCode
import com.botem.domain.wish.repository.WishRepository
import com.botem.global.exception.BusinessException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

@Service
class FriendProfileService(
    private val friendshipRepository: FriendshipRepository,
    private val userRepository: UserRepository,
    private val fundingRepository: FundingRepository,
    private val contributionRepository: ContributionRepository,
    private val wishRepository: WishRepository,
    private val clock: Clock,
) {

    // 친구 목록에서 들어오는 로그인 프로필. 친구가 아니면 위시를 보여줄 수 없으므로 거부한다
    @Transactional(readOnly = true)
    fun getFriendProfile(userId: Long, friendId: Long): FriendProfileResponse {
        val friend = findFriend(userId, friendId)

        return profileOf(target = friend, viewerId = userId, wishesVisible = true)
    }

    // 위시 프로필 링크로 들어오는 프로필. 비로그인도 열 수 있고, 위시는 친구와 본인에게만 보여준다
    @Transactional(readOnly = true)
    fun getWishProfile(viewerId: Long?, wishProfileKey: String): FriendProfileResponse {
        val target = userRepository.findByWishProfileKey(wishProfileKey)
            ?.takeIf { it.status == UserStatus.ACTIVE }
            ?: throw BusinessException(FriendErrorCode.WISH_PROFILE_NOT_FOUND)
        val wishesVisible = viewerId != null &&
            (viewerId == target.id || friendshipRepository.existsByUserIdAndFriendId(viewerId, target.id))

        return profileOf(target = target, viewerId = viewerId, wishesVisible = wishesVisible)
    }

    @Transactional(readOnly = true)
    fun getFriendWish(userId: Long, friendId: Long, wishId: Long): FriendWishResponse {
        findFriend(userId, friendId)
        val wish = wishRepository.findByIdAndUserId(wishId, friendId)
            ?: throw BusinessException(WishErrorCode.WISH_NOT_FOUND)
        val activeSurprise = fundingRepository.findFirstByRecipientIdAndTypeAndRevealAtAfterAndStatusNotIn(
            recipientId = friendId,
            type = FundingType.SURPRISE,
            now = LocalDateTime.now(clock),
            statuses = ENDED_FUNDING_STATUSES,
        )

        return FriendWishResponse(
            wishId = wish.id,
            productName = wish.productName,
            productImageUrl = wish.productImageUrl,
            price = wish.price,
            productUrl = wish.productUrl,
            sellerName = wish.sellerName,
            activeSurpriseFundingId = activeSurprise?.id,
        )
    }

    private fun findFriend(userId: Long, friendId: Long): User {
        if (!friendshipRepository.existsByUserIdAndFriendId(userId, friendId)) {
            throw BusinessException(FriendErrorCode.FRIEND_NOT_FOUND)
        }

        // 탈퇴하면 같은 트랜잭션에서 친구 관계가 지워지므로 친구 관계가 있으면 활성 회원이다
        return userRepository.findById(friendId)
            .orElseThrow { BusinessException(FriendErrorCode.FRIEND_NOT_FOUND) }
    }

    private fun profileOf(target: User, viewerId: Long?, wishesVisible: Boolean): FriendProfileResponse {
        val now = LocalDateTime.now(clock)
        // 서프라이즈는 로그인한 방문자에게만, 받는 사람 본인에게는 보여주지 않는다 (비로그인은 받는 사람일 수 있어 숨김)
        val includeSurprise = viewerId != null && viewerId != target.id
        val fundings = fundingRepository.findProfileFundings(target.id, now, includeSurprise)
        val wishes = findWishesNotInFunding(target.id)
        val birthday = target.birthday

        return FriendProfileResponse(
            userId = target.id,
            name = target.name ?: target.nickname,
            profileImageUrl = target.profileImageUrl,
            birthday = birthday,
            birthdayDday = birthday?.daysUntilNextBirthday(now.toLocalDate()),
            fundings = toFundingResponses(fundings, now),
            wishesVisible = wishesVisible,
            wishCount = wishes.size,
            wishes = if (wishesVisible) wishes.map(::toWishResponse) else emptyList(),
        )
    }

    // ponytail: 위시는 페이징 없이 전부 조회한다. 위시가 수백 개 단위로 늘면 프로필 위시에 페이징 추가
    // 진행 중 펀딩에 쓰인 상품은 펀딩 카드로 보이므로 위시 목록에서 뺀다
    private fun findWishesNotInFunding(userId: Long): List<Wish> {
        val wishes = wishRepository.findAllByUserIdOrderByCreatedAtDesc(userId)
        if (wishes.isEmpty()) {
            return emptyList()
        }

        val fundingWishIds = fundingRepository
            .findAllByOwnerIdAndProductSourceWishIdInAndStatusNotIn(userId, wishes.map(Wish::id), ENDED_FUNDING_STATUSES)
            .mapNotNull { it.product.sourceWishId }
            .toSet()

        return wishes.filterNot { it.id in fundingWishIds }
    }

    private fun toFundingResponses(fundings: List<Funding>, now: LocalDateTime): List<FriendProfileFundingResponse> {
        if (fundings.isEmpty()) {
            return emptyList()
        }

        val fundingIds = fundings.map(Funding::id)
        val summaries = contributionRepository.summarizeByFundingIds(fundingIds)
            .associateBy(FundingContributionSummary::fundingId)
        val firstParticipantNames = contributionRepository.findParticipantPreviewsByFundingIds(fundingIds)
            .groupBy { it.fundingId }
            .mapValues { (_, previews) -> previews.first().participantName }

        return fundings.map { funding ->
            val summary = summaries[funding.id] ?: FundingContributionSummary.empty(funding.id)

            FriendProfileFundingResponse(
                fundingId = funding.id,
                shareKey = funding.shareKey,
                type = funding.type,
                productName = funding.product.name,
                productImageUrl = funding.product.imageUrl,
                deadline = funding.deadline,
                // 서프라이즈는 마감 후 공개 전까지 목록에 남으므로 음수가 되지 않게 0에서 멈춘다
                remainingDays = ChronoUnit.DAYS.between(now.toLocalDate(), funding.deadline.toLocalDate())
                    .coerceAtLeast(0),
                participantCount = summary.participantCount,
                firstParticipantName = firstParticipantNames[funding.id],
                confirmedPercentage = progressPercentage(summary.confirmedAmount, funding.targetAmount),
            )
        }
    }

    private fun toWishResponse(wish: Wish): FriendProfileWishResponse =
        FriendProfileWishResponse(
            wishId = wish.id,
            productName = wish.productName,
            productImageUrl = wish.productImageUrl,
            price = wish.price,
        )

    companion object {
        private val ENDED_FUNDING_STATUSES = setOf(FundingStatus.COMPLETED, FundingStatus.CANCELLED)
    }
}
