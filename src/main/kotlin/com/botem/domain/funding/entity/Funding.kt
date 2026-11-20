package com.botem.domain.funding.entity

import com.botem.domain.funding.exception.FundingErrorCode
import com.botem.global.entity.BaseEntity
import com.botem.global.exception.BusinessException
import com.botem.global.exception.CommonErrorCode
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(
    name = "fundings",
    indexes = [
        Index(name = "idx_fundings_owner_status_deadline", columnList = "owner_id, status, deadline"),
        Index(name = "idx_fundings_recipient_type_reveal", columnList = "recipient_id, funding_type, reveal_at"),
    ],
)
class Funding(
    @Column(nullable = false, updatable = false)
    val ownerId: Long,

    @Column(updatable = false)
    val recipientId: Long?,

    type: FundingType,

    title: String?,

    targetAmount: Long,

    deadline: LocalDateTime,

    revealAt: LocalDateTime?,

    story: String?,

    promise: String?,

    product: FundingProductSnapshot,

    payoutAccount: FundingPayoutAccountSnapshot,

    // 서프라이즈 비밀 노출 옵션. 받는 사람 계정에는 어느 경우에도 노출하지 않는다
    @Column(nullable = false, updatable = false)
    val showOnRecipientProfile: Boolean = false,

    @Column(nullable = false, updatable = false)
    val notifyRecipientFriends: Boolean = false,

    // 공유 링크용 키. 순차 ID로 다른 펀딩을 훑어볼 수 없도록 참여자 API는 이 키로만 펀딩을 찾는다
    @Column(nullable = false, updatable = false, unique = true, length = SHARE_KEY_LENGTH)
    val shareKey: String = generateShareKey(),
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @Enumerated(EnumType.STRING)
    @Column(name = "funding_type", nullable = false, updatable = false, length = 20)
    val type: FundingType = type

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var status: FundingStatus = FundingStatus.FUNDING
        protected set

    // 펀딩은 제목 없이 개설한다. 제목 입력이 있던 시기에 개설된 일반 펀딩만 값을 가진다
    @Column(length = 20)
    var title: String? = title
        protected set

    @Column(nullable = false)
    var targetAmount: Long = targetAmount
        protected set

    @Column(nullable = false)
    var deadline: LocalDateTime = deadline
        protected set

    var revealAt: LocalDateTime? = revealAt
        protected set

    @Column(length = 300)
    var story: String? = story
        protected set

    @Column(length = 300)
    var promise: String? = promise
        protected set

    @Embedded
    var product: FundingProductSnapshot = product
        protected set

    @Embedded
    val payoutAccount: FundingPayoutAccountSnapshot = payoutAccount

    @Column(nullable = false)
    var extensionCount: Int = 0
        protected set

    var extendedAt: LocalDateTime? = null
        protected set

    var earlyClosedAt: LocalDateTime? = null
        protected set

    var productChangedAt: LocalDateTime? = null
        protected set

    var resultOpenedAt: LocalDateTime? = null
        protected set

    var surpriseOpenedAt: LocalDateTime? = null
        protected set

    var giftPreparedAt: LocalDateTime? = null
        protected set

    var completedAt: LocalDateTime? = null
        protected set

    // 보태기 마감(마감일 경과 또는 확정 금액 목표 도달). 메시지는 마감일 전까지 계속 받는다
    fun closeContributions() {
        if (status == FundingStatus.FUNDING) {
            status = FundingStatus.CLOSED
        }
    }

    fun closeEarly(closedAt: LocalDateTime) {
        if (earlyClosedAt != null) {
            return
        }
        if (!deadline.isAfter(closedAt) || status !in EARLY_CLOSEABLE_STATUSES) {
            throw BusinessException(FundingErrorCode.FUNDING_EARLY_CLOSE_NOT_AVAILABLE)
        }

        earlyClosedAt = closedAt
        // 서프라이즈는 메시지 참여도 함께 닫고, 주최자가 선물 준비를 완료해야 공개 대기로 넘어간다
        status = when (type) {
            FundingType.BIRTHDAY -> FundingStatus.CLOSED
            FundingType.SURPRISE -> FundingStatus.GIFT_PREPARING
        }
    }

    // 목표 미달이어도 실패 없이 확정 금액만큼 준비한다. 이미 준비를 완료했으면 그대로 둔다
    fun prepareSurpriseGift(preparedAt: LocalDateTime) {
        if (isSurpriseGiftPrepared()) {
            return
        }
        if (type != FundingType.SURPRISE) {
            throw BusinessException(FundingErrorCode.SURPRISE_FUNDING_REQUIRED)
        }
        if (!isContributionClosed(preparedAt)) {
            throw BusinessException(FundingErrorCode.FUNDING_NOT_ENDED)
        }
        if (status !in SURPRISE_GIFT_PREPARABLE_STATUSES) {
            throw BusinessException(FundingErrorCode.SURPRISE_GIFT_PREPARATION_NOT_AVAILABLE)
        }

        giftPreparedAt = preparedAt
        status = FundingStatus.WAITING_REVEAL
    }

    fun isContributionClosed(now: LocalDateTime): Boolean =
        earlyClosedAt != null || !deadline.isAfter(now)

    fun changeProduct(
        newProduct: FundingProductSnapshot,
        changedAt: LocalDateTime,
    ): FundingProductChangeHistory {
        val inProgress = deadline.isAfter(changedAt) && status in PRODUCT_CHANGEABLE_STATUSES && earlyClosedAt == null
        // 생일 펀딩은 결과를 연 뒤 구매 전까지 '다른 선물 고르기'로 바꿀 수 있다 (FUND-PG-012)
        if (!inProgress && !isGiftChoosable()) {
            throw BusinessException(FundingErrorCode.FUNDING_PRODUCT_CHANGE_NOT_AVAILABLE)
        }

        val history = FundingProductChangeHistory.record(
            fundingId = id,
            previousProduct = product,
            changedProduct = newProduct,
            changedAt = changedAt,
        )
        product = newProduct
        productChangedAt = changedAt

        return history
    }

    fun extendDeadline(
        newDeadline: LocalDateTime,
        requestedAt: LocalDateTime,
        extendedAt: LocalDateTime,
        confirmedAmount: Long,
    ) {
        if (type != FundingType.BIRTHDAY) {
            throw BusinessException(FundingErrorCode.BIRTHDAY_FUNDING_REQUIRED)
        }
        if (earlyClosedAt != null) {
            throw BusinessException(FundingErrorCode.FUNDING_EXTENSION_NOT_AVAILABLE)
        }
        if (deadline.isAfter(extendedAt)) {
            throw BusinessException(FundingErrorCode.FUNDING_EXTENSION_BEFORE_DEADLINE)
        }
        if (extensionCount >= MAX_EXTENSION_COUNT) {
            throw BusinessException(FundingErrorCode.FUNDING_EXTENSION_LIMIT_EXCEEDED)
        }
        if (!hasExtendableStatus()) {
            throw BusinessException(FundingErrorCode.FUNDING_EXTENSION_NOT_AVAILABLE)
        }
        if (confirmedAmount >= targetAmount) {
            throw BusinessException(FundingErrorCode.FUNDING_TARGET_REACHED)
        }
        if (!newDeadline.isAfter(extendedAt) || !newDeadline.isAfter(deadline)) {
            throw BusinessException(FundingErrorCode.FUNDING_EXTENSION_DEADLINE_INVALID)
        }
        if (newDeadline.toLocalDate().isAfter(requestedAt.toLocalDate().plusDays(MAX_EXTENSION_DAYS))) {
            throw BusinessException(FundingErrorCode.FUNDING_EXTENSION_PERIOD_EXCEEDED)
        }

        deadline = newDeadline
        extensionCount += 1
        this.extendedAt = extendedAt
        // 기존 롤링페이퍼는 삭제하지 않고, 연장 기간에 들어온 보탬을 다시 확인할 수 있도록 결과 확정 상태만 되돌린다
        resultOpenedAt = null
        status = FundingStatus.FUNDING
    }

    // 마감 전이라도 목표를 달성하고 확인 대기가 없으면 개설자가 바로 열 수 있다 (홈 '지금 펑! 열까요?')
    fun openResult(openedAt: LocalDateTime, earlyOpenable: Boolean = false) {
        if (resultOpenedAt != null) {
            return
        }
        if (type != FundingType.BIRTHDAY) {
            throw BusinessException(FundingErrorCode.BIRTHDAY_FUNDING_REQUIRED)
        }
        if (deadline.isAfter(openedAt) && !earlyOpenable) {
            throw BusinessException(FundingErrorCode.FUNDING_NOT_ENDED)
        }
        if (status !in RESULT_OPENABLE_STATUSES) {
            throw BusinessException(FundingErrorCode.FUNDING_RESULT_NOT_OPENABLE)
        }

        resultOpenedAt = openedAt
        status = FundingStatus.GIFT_PREPARING
    }

    // 연장 API와 같은 조건이라 결과·관리 화면의 '기간 연장' 노출이 실제 요청 결과와 어긋나지 않는다
    fun isExtendable(now: LocalDateTime, confirmedAmount: Long): Boolean =
        type == FundingType.BIRTHDAY &&
            earlyClosedAt == null &&
            !deadline.isAfter(now) &&
            extensionCount < MAX_EXTENSION_COUNT &&
            hasExtendableStatus() &&
            confirmedAmount < targetAmount

    private fun hasExtendableStatus(): Boolean =
        status in EXTENDABLE_STATUSES && (status == FundingStatus.GIFT_PREPARING) == (resultOpenedAt != null)

    private fun isGiftChoosable(): Boolean =
        type == FundingType.BIRTHDAY && status == FundingStatus.GIFT_PREPARING && resultOpenedAt != null

    // 결과를 연 생일 펀딩에서 구매(쿠팡 이동)를 누르면 지금 상품으로 선물을 정하고 인증 대기로 넘긴다.
    // 인증 대기와 진행 중이 겹치지 않도록 이후에는 기간 연장과 상품 변경이 막힌다
    fun chooseGift() {
        if (type != FundingType.BIRTHDAY) {
            throw BusinessException(FundingErrorCode.BIRTHDAY_FUNDING_REQUIRED)
        }
        if (status == FundingStatus.WAITING_GIFT_VERIFICATION) {
            return
        }
        if (!isGiftChoosable()) {
            throw BusinessException(FundingErrorCode.GIFT_CHOICE_NOT_AVAILABLE)
        }

        status = FundingStatus.WAITING_GIFT_VERIFICATION
    }

    fun isGiftRecipient(userId: Long): Boolean =
        when (type) {
            FundingType.BIRTHDAY -> ownerId == userId
            FundingType.SURPRISE -> recipientId == userId
        }

    fun completeGiftVerification(completedAt: LocalDateTime) {
        if (this.completedAt != null) {
            return
        }
        validateGiftVerificationCompletable()

        status = FundingStatus.COMPLETED
        this.completedAt = completedAt
    }

    fun validateGiftVerificationCompletable() {
        if (completedAt != null) {
            return
        }
        if (status != FundingStatus.WAITING_GIFT_VERIFICATION) {
            throw BusinessException(FundingErrorCode.FUNDING_GIFT_VERIFICATION_NOT_COMPLETABLE)
        }
    }

    // 공개 시각이 지나도 주최자가 선물 준비를 완료하기 전까지는 받는 사람에게 숨긴다
    fun isHiddenFromRecipient(viewerId: Long?, now: LocalDateTime): Boolean =
        type == FundingType.SURPRISE &&
            viewerId == recipientId &&
            (revealAt?.isAfter(now) != false || !isSurpriseGiftPrepared())

    // 준비 완료 이전 데이터에는 giftPreparedAt이 없을 수 있어 상태로 판단한다
    fun isSurpriseGiftPrepared(): Boolean =
        type == FundingType.SURPRISE && status in SURPRISE_REVEAL_VIEWABLE_STATUSES

    fun validateSurpriseRevealAccess(userId: Long, now: LocalDateTime) {
        if (type != FundingType.SURPRISE) {
            throw BusinessException(FundingErrorCode.SURPRISE_FUNDING_REQUIRED)
        }
        if (revealAt?.isAfter(now) != false) {
            throw BusinessException(FundingErrorCode.SURPRISE_REVEAL_NOT_AVAILABLE)
        }
        if (recipientId != userId) {
            throw BusinessException(FundingErrorCode.SURPRISE_RECIPIENT_ONLY)
        }
        if (status !in SURPRISE_REVEAL_VIEWABLE_STATUSES) {
            throw BusinessException(FundingErrorCode.SURPRISE_REVEAL_NOT_OPENABLE)
        }
    }

    fun revealSurprise(userId: Long, openedAt: LocalDateTime) {
        validateSurpriseRevealAccess(userId, openedAt)
        if (surpriseOpenedAt != null) {
            return
        }
        if (status != FundingStatus.WAITING_REVEAL) {
            throw BusinessException(FundingErrorCode.SURPRISE_REVEAL_NOT_OPENABLE)
        }

        surpriseOpenedAt = openedAt
        status = FundingStatus.REVEALED
    }

    fun beginGiftVerification() {
        if (status == FundingStatus.WAITING_GIFT_VERIFICATION) {
            return
        }
        if (type != FundingType.SURPRISE || status != FundingStatus.REVEALED) {
            throw BusinessException(FundingErrorCode.FUNDING_GIFT_VERIFICATION_NOT_COMPLETABLE)
        }

        status = FundingStatus.WAITING_GIFT_VERIFICATION
    }

    companion object {
        private const val SHARE_KEY_LENGTH = 32
        private val SHARE_KEY_REGEX = Regex("[0-9a-f]{$SHARE_KEY_LENGTH}")
        private const val MAX_TARGET_AMOUNT = 10_000_000L

        fun generateShareKey(): String = UUID.randomUUID().toString().replace("-", "")

        fun isShareKeyFormat(value: String): Boolean = SHARE_KEY_REGEX.matches(value)
        private const val MAX_STORY_LENGTH = 300
        private const val MAX_PROMISE_LENGTH = 300
        private const val MAX_EXTENSION_COUNT = 1
        private const val MAX_EXTENSION_DAYS = 14L

        fun createBirthday(
            ownerId: Long,
            targetAmount: Long,
            deadline: LocalDateTime,
            story: String,
            promise: String?,
            product: FundingProductSnapshot,
            payoutAccount: FundingPayoutAccountSnapshot,
            now: LocalDateTime,
        ): Funding {
            val normalizedStory = story.normalizeRequired("펀딩 사연")
            val normalizedPromise = promise.normalizeOptional()

            validateLength(normalizedStory, MAX_STORY_LENGTH, "펀딩 사연")
            normalizedPromise?.let { validateLength(it, MAX_PROMISE_LENGTH, "공약") }
            validateTargetAmount(targetAmount)
            validateDeadline(deadline, now)

            return Funding(
                ownerId = ownerId,
                recipientId = null,
                type = FundingType.BIRTHDAY,
                title = null,
                targetAmount = targetAmount,
                deadline = deadline,
                revealAt = null,
                story = normalizedStory,
                promise = normalizedPromise,
                product = product,
                payoutAccount = payoutAccount,
            )
        }

        fun createSurprise(
            ownerId: Long,
            recipientId: Long,
            targetAmount: Long,
            deadline: LocalDateTime,
            revealAt: LocalDateTime,
            showOnRecipientProfile: Boolean,
            notifyRecipientFriends: Boolean,
            product: FundingProductSnapshot,
            payoutAccount: FundingPayoutAccountSnapshot,
            now: LocalDateTime,
        ): Funding {
            validateTargetAmount(targetAmount)
            validateDeadline(deadline, now)
            // 마감(생일 전날) 다음 날 안에서만 공개한다
            if (!revealAt.isAfter(deadline) || revealAt.toLocalDate().isAfter(deadline.toLocalDate().plusDays(1))) {
                throw BusinessException(
                    CommonErrorCode.INVALID_INPUT_VALUE,
                    "공개 시각은 마감 이후부터 마감일 다음 날까지 선택할 수 있습니다.",
                )
            }

            return Funding(
                ownerId = ownerId,
                recipientId = recipientId,
                type = FundingType.SURPRISE,
                title = null,
                targetAmount = targetAmount,
                deadline = deadline,
                revealAt = revealAt,
                story = null,
                promise = null,
                product = product,
                payoutAccount = payoutAccount,
                showOnRecipientProfile = showOnRecipientProfile,
                notifyRecipientFriends = notifyRecipientFriends,
            )
        }

        private fun String.normalizeRequired(fieldName: String): String =
            normalizeOptional()
                ?: throw BusinessException(
                    CommonErrorCode.INVALID_INPUT_VALUE,
                    "${fieldName}은(는) 필수입니다.",
                )

        private fun String?.normalizeOptional(): String? =
            this
                ?.trim { it.isWhitespace() || Character.isSpaceChar(it) }
                ?.takeIf(String::isNotEmpty)

        private fun validateLength(value: String, maxLength: Int, fieldName: String) {
            if (value.length > maxLength) {
                throw BusinessException(
                    CommonErrorCode.INVALID_INPUT_VALUE,
                    "${fieldName}은(는) ${maxLength}자 이하여야 합니다.",
                )
            }
        }

        private fun validateTargetAmount(targetAmount: Long) {
            if (targetAmount !in 1..MAX_TARGET_AMOUNT) {
                throw BusinessException(
                    CommonErrorCode.INVALID_INPUT_VALUE,
                    "목표 금액은 1원 이상 1,000만 원 이하여야 합니다.",
                )
            }
        }

        private fun validateDeadline(deadline: LocalDateTime, now: LocalDateTime) {
            if (!deadline.toLocalDate().isAfter(now.toLocalDate())) {
                throw BusinessException(
                    CommonErrorCode.INVALID_INPUT_VALUE,
                    "마감일은 내일부터 선택할 수 있습니다.",
                )
            }
        }

        private val RESULT_OPENABLE_STATUSES = setOf(
            FundingStatus.FUNDING,
            FundingStatus.CLOSED,
        )
        private val EXTENDABLE_STATUSES = setOf(
            FundingStatus.FUNDING,
            FundingStatus.CLOSED,
            FundingStatus.GIFT_PREPARING,
        )
        private val EARLY_CLOSEABLE_STATUSES = setOf(
            FundingStatus.FUNDING,
            FundingStatus.CLOSED,
        )
        private val PRODUCT_CHANGEABLE_STATUSES = setOf(
            FundingStatus.FUNDING,
            FundingStatus.CLOSED,
        )
        private val SURPRISE_GIFT_PREPARABLE_STATUSES = setOf(
            FundingStatus.FUNDING,
            FundingStatus.CLOSED,
            FundingStatus.GIFT_PREPARING,
        )
        private val SURPRISE_REVEAL_VIEWABLE_STATUSES = setOf(
            FundingStatus.WAITING_REVEAL,
            FundingStatus.REVEALED,
            FundingStatus.WAITING_GIFT_VERIFICATION,
            FundingStatus.COMPLETED,
        )
    }
}
