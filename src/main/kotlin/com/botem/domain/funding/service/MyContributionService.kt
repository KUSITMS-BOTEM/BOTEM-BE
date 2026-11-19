package com.botem.domain.funding.service

import com.botem.domain.account.service.AccountNumberCipher
import com.botem.domain.funding.dto.request.UpdateMyContributionRequest
import com.botem.domain.funding.dto.response.ContributionPayoutAccountResponse
import com.botem.domain.funding.dto.response.MyContributionListResponse
import com.botem.domain.funding.dto.response.MyContributionResponse
import com.botem.domain.funding.entity.Contribution
import com.botem.domain.funding.entity.ContributionStatus
import com.botem.domain.funding.entity.Funding
import com.botem.domain.funding.entity.FundingStatus
import com.botem.domain.funding.entity.RollingPaperMessage
import com.botem.domain.funding.exception.FundingErrorCode
import com.botem.domain.funding.repository.ContributionRepository
import com.botem.domain.funding.repository.FundingRepository
import com.botem.domain.funding.repository.RollingPaperMessageRepository
import com.botem.global.exception.BusinessException
import com.botem.global.exception.CommonErrorCode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

// 로그인 회원이 펀딩 페이지에서 자기 보탬을 확인하고 고친다. 비회원은 GuestContributionService가 맡는다
@Service
class MyContributionService(
    private val fundingRepository: FundingRepository,
    private val contributionRepository: ContributionRepository,
    private val rollingPaperMessageRepository: RollingPaperMessageRepository,
    private val accountNumberCipher: AccountNumberCipher,
    private val clock: Clock,
) {

    @Transactional(readOnly = true)
    fun getMyContributions(userId: Long, fundingId: Long): MyContributionListResponse {
        val now = LocalDateTime.now(clock)
        val funding = fundingRepository.findById(fundingId).orElse(null)
            ?.takeIf { it.status != FundingStatus.CANCELLED }
            ?: throw BusinessException(FundingErrorCode.FUNDING_NOT_FOUND)
        validateVisibleTo(funding, userId, now)

        val contributions = contributionRepository.findAllByFundingIdAndParticipantUserIdAndStatusNotOrderByCreatedAtAscIdAsc(
            fundingId,
            userId,
            ContributionStatus.CANCELLED,
        )
        val messages = if (contributions.isEmpty()) {
            emptyMap()
        } else {
            rollingPaperMessageRepository.findAllByContributionIdIn(contributions.map(Contribution::id))
                .associateBy(RollingPaperMessage::contributionId)
        }
        val payoutAccount by lazy { ContributionPayoutAccountResponse.of(funding, accountNumberCipher) }

        return MyContributionListResponse(
            editable = isEditable(funding, now),
            contributions = contributions.map { contribution ->
                toResponse(
                    contribution = contribution,
                    message = messages[contribution.id],
                    funding = funding,
                    payoutAccount = if (contribution.status in TRANSFER_REQUIRED_STATUSES) payoutAccount else null,
                )
            },
        )
    }

    @Transactional
    fun updateMyContribution(
        userId: Long,
        fundingId: Long,
        contributionId: Long,
        request: UpdateMyContributionRequest,
    ): MyContributionResponse {
        val participantName = request.participantName.normalizeRequired("참여자 이름을 확인해 주세요.")
        val messageContent = request.message.normalizeRequired("축하 메시지는 필수입니다.")
        val paperTemplateCode = request.paperTemplateCode.normalizeRequired("편지지를 선택해야 합니다.")
        // 보태기·비회원 수정과 같은 순서로 펀딩부터 잠가 입금 확인·결과 공개와 직렬화한다
        val funding = fundingRepository.findByIdForUpdate(fundingId)
            ?.takeIf { it.status != FundingStatus.CANCELLED }
            ?: throw BusinessException(FundingErrorCode.FUNDING_NOT_FOUND)
        val now = LocalDateTime.now(clock)
        // 보탬 존재 여부에 따라 오류가 달라지기 전에 막아, 받는 사람이 오류 차이로 서프라이즈를 알아채지 못하게 한다
        validateVisibleTo(funding, userId, now)
        val contribution = contributionRepository.findByIdAndFundingIdForUpdate(contributionId, fundingId)
            ?.takeIf { it.status != ContributionStatus.CANCELLED }
            ?: throw BusinessException(FundingErrorCode.CONTRIBUTION_NOT_FOUND)
        if (contribution.participantUserId != userId) {
            throw BusinessException(FundingErrorCode.CONTRIBUTION_PARTICIPANT_ONLY)
        }
        if (!isEditable(funding, now)) {
            throw BusinessException(FundingErrorCode.CONTRIBUTION_NOT_EDITABLE)
        }

        contribution.updateParticipantName(participantName)
        val message = rollingPaperMessageRepository.findByContributionId(contribution.id)
            ?.also { it.updateTextContent(messageContent, paperTemplateCode) }
            ?: rollingPaperMessageRepository.save(
                RollingPaperMessage(
                    contributionId = contribution.id,
                    content = messageContent,
                    paperTemplateCode = paperTemplateCode,
                ),
            )

        return toResponse(contribution, message, funding, payoutAccount = null)
    }

    // 공개 전 서프라이즈를 받는 사람에게는 펀딩이 있다는 사실도 드러내지 않는다
    private fun validateVisibleTo(funding: Funding, userId: Long, now: LocalDateTime) {
        if (funding.isHiddenFromRecipient(userId, now)) {
            throw BusinessException(FundingErrorCode.SURPRISE_NOT_REVEALED)
        }
    }

    // 메시지로 보태기와 같은 기준으로 연다. 생일은 조기 마감해도 마감일까지 메시지를 받고,
    // 서프라이즈는 조기 마감하면 선물 준비로 넘어가 메시지도 닫힌다
    private fun isEditable(funding: Funding, now: LocalDateTime): Boolean =
        funding.status in EDITABLE_FUNDING_STATUSES && funding.deadline.isAfter(now)

    private fun toResponse(
        contribution: Contribution,
        message: RollingPaperMessage?,
        funding: Funding,
        payoutAccount: ContributionPayoutAccountResponse?,
    ): MyContributionResponse =
        MyContributionResponse(
            contributionId = contribution.id,
            participantName = contribution.participantName,
            amount = contribution.amount,
            status = contribution.status,
            excludedFromResult = contribution.status == ContributionStatus.PENDING_CONFIRMATION &&
                (funding.resultOpenedAt != null || funding.isSurpriseGiftPrepared()),
            message = message?.content,
            paperTemplateCode = message?.paperTemplateCode,
            decorationCode = message?.decorationCode,
            photoUrl = message?.photoUrl,
            voiceUrl = message?.voiceUrl,
            voiceDurationSeconds = message?.voiceDurationSeconds,
            participatedAt = contribution.createdAt,
            payoutAccount = payoutAccount,
        )

    private fun String.normalizeRequired(detail: String): String =
        trim { it.isWhitespace() || Character.isSpaceChar(it) }
            .takeIf(String::isNotEmpty)
            ?: throw BusinessException(CommonErrorCode.INVALID_INPUT_VALUE, detail)

    companion object {
        private val EDITABLE_FUNDING_STATUSES = setOf(FundingStatus.FUNDING, FundingStatus.CLOSED)
        private val TRANSFER_REQUIRED_STATUSES = setOf(
            ContributionStatus.AWAITING_TRANSFER,
            ContributionStatus.REJECTED,
        )
    }
}
