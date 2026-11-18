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

    title: String,

    targetAmount: Long,

    deadline: LocalDateTime,

    revealAt: LocalDateTime?,

    story: String?,

    promise: String?,

    product: FundingProductSnapshot,

    payoutAccount: FundingPayoutAccountSnapshot,
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

    @Column(nullable = false, length = 20)
    var title: String = title
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
        status = when (type) {
            FundingType.BIRTHDAY -> FundingStatus.CLOSED
            FundingType.SURPRISE -> FundingStatus.WAITING_REVEAL
        }
    }

    fun changeProduct(
        newProduct: FundingProductSnapshot,
        changedAt: LocalDateTime,
    ): FundingProductChangeHistory? {
        if (!deadline.isAfter(changedAt) || status !in PRODUCT_CHANGEABLE_STATUSES || earlyClosedAt != null) {
            throw BusinessException(FundingErrorCode.FUNDING_PRODUCT_CHANGE_NOT_AVAILABLE)
        }
        if (product.sourceWishId != null && product.sourceWishId == newProduct.sourceWishId) {
            return null
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
        if (deadline.isAfter(extendedAt)) {
            throw BusinessException(FundingErrorCode.FUNDING_EXTENSION_BEFORE_DEADLINE)
        }
        if (extensionCount >= MAX_EXTENSION_COUNT) {
            throw BusinessException(FundingErrorCode.FUNDING_EXTENSION_LIMIT_EXCEEDED)
        }
        if (
            earlyClosedAt != null ||
            status !in EXTENDABLE_STATUSES ||
            (status == FundingStatus.GIFT_PREPARING) != (resultOpenedAt != null)
        ) {
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

    fun openResult(openedAt: LocalDateTime) {
        if (resultOpenedAt != null) {
            return
        }
        if (type != FundingType.BIRTHDAY) {
            throw BusinessException(FundingErrorCode.BIRTHDAY_FUNDING_REQUIRED)
        }
        if (deadline.isAfter(openedAt)) {
            throw BusinessException(FundingErrorCode.FUNDING_NOT_ENDED)
        }
        if (status !in RESULT_OPENABLE_STATUSES) {
            throw BusinessException(FundingErrorCode.FUNDING_RESULT_NOT_OPENABLE)
        }

        resultOpenedAt = openedAt
        status = FundingStatus.GIFT_PREPARING
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
        private const val MAX_TITLE_LENGTH = 20
        private const val MAX_TARGET_AMOUNT = 10_000_000L
        private const val MAX_STORY_LENGTH = 300
        private const val MAX_PROMISE_LENGTH = 300
        private const val MAX_EXTENSION_COUNT = 1
        private const val MAX_EXTENSION_DAYS = 14L

        fun createBirthday(
            ownerId: Long,
            title: String,
            targetAmount: Long,
            deadline: LocalDateTime,
            story: String,
            promise: String?,
            product: FundingProductSnapshot,
            payoutAccount: FundingPayoutAccountSnapshot,
            now: LocalDateTime,
        ): Funding {
            val normalizedTitle = title.normalizeRequired("펀딩 제목")
            val normalizedStory = story.normalizeRequired("펀딩 사연")
            val normalizedPromise = promise.normalizeOptional()

            validateLength(normalizedTitle, MAX_TITLE_LENGTH, "펀딩 제목")
            validateLength(normalizedStory, MAX_STORY_LENGTH, "펀딩 사연")
            normalizedPromise?.let { validateLength(it, MAX_PROMISE_LENGTH, "공약") }
            validateTargetAmount(targetAmount)
            validateDeadline(deadline, now)

            return Funding(
                ownerId = ownerId,
                recipientId = null,
                type = FundingType.BIRTHDAY,
                title = normalizedTitle,
                targetAmount = targetAmount,
                deadline = deadline,
                revealAt = null,
                story = normalizedStory,
                promise = normalizedPromise,
                product = product,
                payoutAccount = payoutAccount,
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
        private val SURPRISE_REVEAL_VIEWABLE_STATUSES = setOf(
            FundingStatus.WAITING_REVEAL,
            FundingStatus.REVEALED,
            FundingStatus.WAITING_GIFT_VERIFICATION,
            FundingStatus.COMPLETED,
        )
    }
}
