package com.botem.domain.funding.service

import com.botem.domain.funding.dto.response.FundingArchiveCardResponse
import com.botem.domain.funding.dto.response.FundingArchiveRelationship
import com.botem.domain.funding.dto.response.FundingArchiveRepresentativeMessageResponse
import com.botem.domain.funding.dto.response.FundingArchiveResponse
import com.botem.domain.funding.dto.response.FundingArchiveTab
import com.botem.domain.funding.dto.response.FundingArchiveTabCounts
import com.botem.domain.funding.dto.response.FundingArchiveUserResponse
import com.botem.domain.funding.dto.response.FundingArchiveYearResponse
import com.botem.domain.funding.entity.Funding
import com.botem.domain.funding.entity.FundingType
import com.botem.domain.funding.repository.ContributionRepository
import com.botem.domain.funding.repository.FundingRepository
import com.botem.domain.funding.repository.RollingPaperMessageRepository
import com.botem.domain.funding.repository.projection.ArchiveRollingPaperSummaryProjection
import com.botem.domain.user.entity.User
import com.botem.domain.user.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

@Service
class FundingArchiveService(
    private val fundingRepository: FundingRepository,
    private val contributionRepository: ContributionRepository,
    private val rollingPaperMessageRepository: RollingPaperMessageRepository,
    private val userRepository: UserRepository,
    private val clock: Clock,
) {

    // 아카이브 화면은 아직 사용자의 누적 펀딩 수가 작아 전체를 연도별로 반환한다.
    // 목록이 길어지면 연도 단위 또는 커서 기반 페이지네이션을 추가한다.
    @Transactional(readOnly = true)
    fun getArchive(userId: Long, tab: FundingArchiveTab): FundingArchiveResponse {
        val now = LocalDateTime.now(clock)
        val fundings = fundingRepository.findArchivedFundingsByUserId(userId, now)
        if (fundings.isEmpty()) {
            return FundingArchiveResponse(
                tabCounts = FundingArchiveTabCounts(all = 0, received = 0, participated = 0),
                years = emptyList(),
            )
        }

        val fundingIds = fundings.map(Funding::id)
        val participatedFundingIds = contributionRepository.findParticipatedFundingIds(fundingIds, userId).toSet()
        val rollingPaperViewableFundingIds = fundings.filter { it.isRollingPaperViewable(now) }.map(Funding::id).toSet()
        val summariesByFundingId = if (rollingPaperViewableFundingIds.isEmpty()) {
            emptyMap()
        } else {
            rollingPaperMessageRepository.findArchiveSummariesByFundingIds(rollingPaperViewableFundingIds)
                .associateBy(ArchiveRollingPaperSummaryProjection::fundingId)
        }
        val usersById = userRepository.findAllById(
            fundings.flatMap { listOfNotNull(it.ownerId, it.recipientId) }.distinct(),
        ).associateBy(User::id)

        val cards = fundings.map { funding ->
            funding.toArchiveCard(
                userId,
                participatedFundingIds,
                rollingPaperViewableFundingIds,
                usersById,
                summariesByFundingId,
            )
        }
        val tabCounts = FundingArchiveTabCounts(
            all = cards.size,
            received = cards.count { it.relationship == FundingArchiveRelationship.RECEIVED },
            participated = cards.count { it.relationship == FundingArchiveRelationship.PARTICIPATED },
        )

        return FundingArchiveResponse(
            tabCounts = tabCounts,
            years = cards.asSequence()
                .filter { it.matches(tab) }
                .groupBy { it.deadline.year }
                .map { (year, yearCards) ->
                    FundingArchiveYearResponse(
                        year = year,
                        fundingCount = yearCards.size,
                        fundings = yearCards,
                    )
                }
                .toList(),
        )
    }

    private fun Funding.toArchiveCard(
        userId: Long,
        participatedFundingIds: Set<Long>,
        rollingPaperViewableFundingIds: Set<Long>,
        usersById: Map<Long, User>,
        summariesByFundingId: Map<Long, ArchiveRollingPaperSummaryProjection>,
    ): FundingArchiveCardResponse {
        val relationship = when {
            ownerId == userId -> FundingArchiveRelationship.OPENED
            recipientId == userId -> FundingArchiveRelationship.RECEIVED
            id in participatedFundingIds -> FundingArchiveRelationship.PARTICIPATED
            else -> error("아카이브 대상은 개설자·수신자·참여자 중 하나여야 합니다.")
        }
        val opener = usersById[ownerId]
        val celebrantId = if (type == FundingType.BIRTHDAY) ownerId else requireNotNull(recipientId)
        val celebrant = usersById[celebrantId]
        val isRollingPaperViewable = id in rollingPaperViewableFundingIds
        val summary = summariesByFundingId[id].takeIf { isRollingPaperViewable }

        return FundingArchiveCardResponse(
            fundingId = id,
            relationship = relationship,
            fundingType = type,
            opener = FundingArchiveUserResponse(
                name = opener?.displayName.orEmpty(),
                profileImageUrl = opener?.profileImageUrl,
            ),
            celebrantName = celebrant?.displayName.orEmpty(),
            deadline = deadline,
            productName = product.name,
            productImageUrl = product.imageUrl,
            rollingPaperCount = if (isRollingPaperViewable) summary?.messageCount ?: 0 else null,
            representativeMessage = summary?.let {
                FundingArchiveRepresentativeMessageResponse(
                    writerName = it.writerName,
                    writerProfileImageUrl = it.writerProfileImageUrl,
                    content = it.content,
                    paperTemplateCode = it.paperTemplateCode,
                    hasVoice = it.voiceUrl != null,
                )
            },
        )
    }

    private fun FundingArchiveCardResponse.matches(tab: FundingArchiveTab): Boolean =
        when (tab) {
            FundingArchiveTab.ALL -> true
            FundingArchiveTab.RECEIVED -> relationship == FundingArchiveRelationship.RECEIVED
            FundingArchiveTab.PARTICIPATED -> relationship == FundingArchiveRelationship.PARTICIPATED
        }

    private val User.displayName: String
        get() = name ?: nickname
}
