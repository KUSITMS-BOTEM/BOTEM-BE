package com.botem.domain.funding.entity

import com.botem.domain.account.entity.Bank
import com.botem.domain.funding.exception.FundingErrorCode
import com.botem.global.exception.BusinessException
import com.botem.global.exception.CommonErrorCode
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class FundingTest {

    @Test
    fun `일반 펀딩 생성 시 문자열을 정규화하고 생일 펀딩 규칙을 적용한다`() {
        val funding = Funding.createBirthday(
            ownerId = 1L,
            title = "  생일 선물 펀딩  ",
            targetAmount = 100_000L,
            deadline = OPENED_AT.plusDays(1),
            story = "  새 헤드폰이 필요해요  ",
            promise = "　 ",
            product = product(),
            payoutAccount = payoutAccount(),
            now = OPENED_AT,
        )

        assertEquals(FundingType.BIRTHDAY, funding.type)
        assertEquals(null, funding.recipientId)
        assertEquals(null, funding.revealAt)
        assertEquals("생일 선물 펀딩", funding.title)
        assertEquals("새 헤드폰이 필요해요", funding.story)
        assertEquals(null, funding.promise)
    }

    @Test
    fun `일반 펀딩 제목과 사연은 공백일 수 없다`() {
        val titleException = assertFailsWith<BusinessException> {
            createBirthday(title = "　 ")
        }
        val storyException = assertFailsWith<BusinessException> {
            createBirthday(story = "\u00A0")
        }

        assertEquals(CommonErrorCode.INVALID_INPUT_VALUE, titleException.errorCode)
        assertEquals(CommonErrorCode.INVALID_INPUT_VALUE, storyException.errorCode)
    }

    @Test
    fun `일반 펀딩 입력 문자열은 최대 길이를 넘을 수 없다`() {
        val titleException = assertFailsWith<BusinessException> {
            createBirthday(title = "가".repeat(21))
        }
        val storyException = assertFailsWith<BusinessException> {
            createBirthday(story = "가".repeat(301))
        }
        val promiseException = assertFailsWith<BusinessException> {
            createBirthday(promise = "가".repeat(301))
        }

        assertEquals(CommonErrorCode.INVALID_INPUT_VALUE, titleException.errorCode)
        assertEquals(CommonErrorCode.INVALID_INPUT_VALUE, storyException.errorCode)
        assertEquals(CommonErrorCode.INVALID_INPUT_VALUE, promiseException.errorCode)
    }

    @Test
    fun `일반 펀딩 목표 금액은 1원 이상 1천만 원 이하여야 한다`() {
        val minimumException = assertFailsWith<BusinessException> {
            createBirthday(targetAmount = 0L)
        }
        val maximumException = assertFailsWith<BusinessException> {
            createBirthday(targetAmount = 10_000_001L)
        }

        assertEquals(CommonErrorCode.INVALID_INPUT_VALUE, minimumException.errorCode)
        assertEquals(CommonErrorCode.INVALID_INPUT_VALUE, maximumException.errorCode)
    }

    @Test
    fun `일반 펀딩 마감일은 내일부터 선택할 수 있다`() {
        val exception = assertFailsWith<BusinessException> {
            createBirthday(deadline = OPENED_AT.plusHours(1))
        }

        assertEquals(CommonErrorCode.INVALID_INPUT_VALUE, exception.errorCode)
    }

    @Test
    fun `새 펀딩은 전달받은 정보와 초기 상태를 가진다`() {
        val deadline = OPENED_AT.plusDays(1)
        val funding = funding(deadline = deadline)

        assertEquals(1L, funding.ownerId)
        assertEquals(null, funding.recipientId)
        assertEquals(FundingType.BIRTHDAY, funding.type)
        assertEquals("생일 선물 펀딩", funding.title)
        assertEquals(100_000L, funding.targetAmount)
        assertEquals(deadline, funding.deadline)
        assertEquals(null, funding.revealAt)
        assertEquals(null, funding.story)
        assertEquals(null, funding.promise)
        assertEquals("무선 헤드폰", funding.product.name)
        assertEquals(Bank.KAKAO_BANK, funding.payoutAccount.bank)
        assertEquals(FundingStatus.FUNDING, funding.status)
        assertEquals(0, funding.extensionCount)
        assertEquals(null, funding.extendedAt)
        assertEquals(null, funding.resultOpenedAt)
        assertEquals(null, funding.surpriseOpenedAt)
        assertEquals(null, funding.giftPreparedAt)
        assertEquals(null, funding.completedAt)
    }

    @Test
    fun `마감된 생일 펀딩 결과를 열면 열람 시각을 기록하고 선물 준비 상태로 전환한다`() {
        val funding = funding(deadline = OPENED_AT.minusDays(1))

        funding.openResult(OPENED_AT)

        assertEquals(OPENED_AT, funding.resultOpenedAt)
        assertEquals(FundingStatus.GIFT_PREPARING, funding.status)
    }

    @Test
    fun `이미 연 펀딩 결과를 다시 열면 최초 열람 상태를 유지한다`() {
        val funding = funding(deadline = OPENED_AT.minusDays(1))
        funding.openResult(OPENED_AT)

        funding.openResult(OPENED_AT.plusHours(1))

        assertEquals(OPENED_AT, funding.resultOpenedAt)
        assertEquals(FundingStatus.GIFT_PREPARING, funding.status)
    }

    @Test
    fun `서프라이즈 펀딩 결과는 팡 열기로 처리할 수 없다`() {
        val funding = funding(
            type = FundingType.SURPRISE,
            deadline = OPENED_AT.minusDays(1),
            revealAt = OPENED_AT.minusHours(1),
        )

        val exception = assertFailsWith<BusinessException> { funding.openResult(OPENED_AT) }

        assertEquals(FundingErrorCode.BIRTHDAY_FUNDING_REQUIRED, exception.errorCode)
    }

    @Test
    fun `마감 전 펀딩 결과는 열 수 없다`() {
        val funding = funding(deadline = OPENED_AT.plusMinutes(1))

        val exception = assertFailsWith<BusinessException> { funding.openResult(OPENED_AT) }

        assertEquals(FundingErrorCode.FUNDING_NOT_ENDED, exception.errorCode)
    }

    @Test
    fun `결과 열람이 불가능한 상태의 펀딩은 열 수 없다`() {
        val funding = funding(deadline = OPENED_AT.minusDays(1))
        setField(funding, "status", FundingStatus.CANCELLED)

        val exception = assertFailsWith<BusinessException> { funding.openResult(OPENED_AT) }

        assertEquals(FundingErrorCode.FUNDING_RESULT_NOT_OPENABLE, exception.errorCode)
    }

    @Test
    fun `생일 펀딩 개설자와 서프라이즈 수신자를 선물 수신자로 판단한다`() {
        val birthday = funding(deadline = OPENED_AT)
        val surprise = funding(
            type = FundingType.SURPRISE,
            deadline = OPENED_AT,
            revealAt = OPENED_AT,
        )

        assertEquals(true, birthday.isGiftRecipient(1L))
        assertEquals(false, birthday.isGiftRecipient(2L))
        assertEquals(true, surprise.isGiftRecipient(2L))
        assertEquals(false, surprise.isGiftRecipient(1L))
    }

    @Test
    fun `선물 인증 대기 펀딩을 완료하면 상태와 완료 시각을 변경한다`() {
        val funding = funding(deadline = OPENED_AT.minusDays(1))
        setField(funding, "status", FundingStatus.WAITING_GIFT_VERIFICATION)

        funding.completeGiftVerification(OPENED_AT)

        assertEquals(FundingStatus.COMPLETED, funding.status)
        assertEquals(OPENED_AT, funding.completedAt)
    }

    @Test
    fun `완료된 펀딩의 선물 인증 완료 재요청은 최초 완료 상태를 유지한다`() {
        val funding = funding(deadline = OPENED_AT.minusDays(1))
        setField(funding, "status", FundingStatus.WAITING_GIFT_VERIFICATION)
        funding.completeGiftVerification(OPENED_AT)

        funding.completeGiftVerification(OPENED_AT.plusHours(1))

        assertEquals(OPENED_AT, funding.completedAt)
    }

    @Test
    fun `선물 인증 대기가 아닌 펀딩은 완료할 수 없다`() {
        val funding = funding(deadline = OPENED_AT.minusDays(1))

        val exception = assertFailsWith<BusinessException> {
            funding.completeGiftVerification(OPENED_AT)
        }

        assertEquals(FundingErrorCode.FUNDING_GIFT_VERIFICATION_NOT_COMPLETABLE, exception.errorCode)
    }

    @Test
    fun `공개 시각이 지난 서프라이즈를 수신자가 열면 공개 상태로 전환한다`() {
        val funding = funding(
            type = FundingType.SURPRISE,
            deadline = OPENED_AT.minusDays(1),
            revealAt = OPENED_AT.minusMinutes(1),
        )
        setField(funding, "status", FundingStatus.WAITING_REVEAL)

        funding.revealSurprise(userId = 2L, openedAt = OPENED_AT)

        assertEquals(FundingStatus.REVEALED, funding.status)
        assertEquals(OPENED_AT, funding.surpriseOpenedAt)

        funding.revealSurprise(userId = 2L, openedAt = OPENED_AT.plusHours(1))
        assertEquals(OPENED_AT, funding.surpriseOpenedAt)
    }

    @Test
    fun `공개 전에는 수신자가 아니어도 서프라이즈 존재를 노출하지 않는다`() {
        val beforeReveal = funding(
            type = FundingType.SURPRISE,
            deadline = OPENED_AT.minusDays(1),
            revealAt = OPENED_AT.plusMinutes(1),
        )
        setField(beforeReveal, "status", FundingStatus.WAITING_REVEAL)

        val beforeRevealException = assertFailsWith<BusinessException> {
            beforeReveal.revealSurprise(userId = 3L, openedAt = OPENED_AT)
        }
        assertEquals(FundingErrorCode.SURPRISE_REVEAL_NOT_AVAILABLE, beforeRevealException.errorCode)

        val revealed = funding(
            type = FundingType.SURPRISE,
            deadline = OPENED_AT.minusDays(1),
            revealAt = OPENED_AT.minusMinutes(1),
        )
        setField(revealed, "status", FundingStatus.WAITING_REVEAL)
        val recipientException = assertFailsWith<BusinessException> {
            revealed.revealSurprise(userId = 3L, openedAt = OPENED_AT)
        }
        assertEquals(FundingErrorCode.SURPRISE_RECIPIENT_ONLY, recipientException.errorCode)
    }

    @Test
    fun `생일 펀딩이거나 공개 불가 상태인 서프라이즈는 열 수 없다`() {
        val birthday = funding(deadline = OPENED_AT.minusDays(1))
        val typeException = assertFailsWith<BusinessException> {
            birthday.revealSurprise(userId = 1L, openedAt = OPENED_AT)
        }
        assertEquals(FundingErrorCode.SURPRISE_FUNDING_REQUIRED, typeException.errorCode)

        val cancelled = funding(
            type = FundingType.SURPRISE,
            deadline = OPENED_AT.minusDays(1),
            revealAt = OPENED_AT.minusMinutes(1),
        )
        setField(cancelled, "status", FundingStatus.CANCELLED)
        val statusException = assertFailsWith<BusinessException> {
            cancelled.revealSurprise(userId = 2L, openedAt = OPENED_AT)
        }
        assertEquals(FundingErrorCode.SURPRISE_REVEAL_NOT_OPENABLE, statusException.errorCode)
    }

    @Test
    fun `공개 시각이 없거나 수신자가 없는 서프라이즈는 공개할 수 없다`() {
        val noRevealAt = funding(
            type = FundingType.SURPRISE,
            deadline = OPENED_AT.minusDays(1),
            revealAt = null,
        )
        setField(noRevealAt, "status", FundingStatus.WAITING_REVEAL)
        val revealAtException = assertFailsWith<BusinessException> {
            noRevealAt.revealSurprise(userId = 2L, openedAt = OPENED_AT)
        }
        assertEquals(FundingErrorCode.SURPRISE_REVEAL_NOT_AVAILABLE, revealAtException.errorCode)

        val noRecipient = funding(
            type = FundingType.SURPRISE,
            deadline = OPENED_AT.minusDays(1),
            revealAt = OPENED_AT.minusMinutes(1),
        )
        setField(noRecipient, "recipientId", null)
        assertEquals(false, noRecipient.isGiftRecipient(2L))
        val recipientException = assertFailsWith<BusinessException> {
            noRecipient.revealSurprise(userId = 2L, openedAt = OPENED_AT)
        }
        assertEquals(FundingErrorCode.SURPRISE_RECIPIENT_ONLY, recipientException.errorCode)
    }

    @Test
    fun `열람 기록 없이 이미 공개 상태인 서프라이즈는 다시 열 수 없다`() {
        val funding = funding(
            type = FundingType.SURPRISE,
            deadline = OPENED_AT.minusDays(1),
            revealAt = OPENED_AT.minusMinutes(1),
        )
        setField(funding, "status", FundingStatus.REVEALED)

        val exception = assertFailsWith<BusinessException> {
            funding.revealSurprise(userId = 2L, openedAt = OPENED_AT)
        }

        assertEquals(FundingErrorCode.SURPRISE_REVEAL_NOT_OPENABLE, exception.errorCode)
    }

    @Test
    fun `공개된 서프라이즈는 선물 인증 대기 상태로 전환한다`() {
        val funding = funding(
            type = FundingType.SURPRISE,
            deadline = OPENED_AT.minusDays(1),
            revealAt = OPENED_AT.minusMinutes(1),
        )
        setField(funding, "status", FundingStatus.WAITING_REVEAL)
        funding.revealSurprise(userId = 2L, openedAt = OPENED_AT)

        funding.beginGiftVerification()
        funding.beginGiftVerification()

        assertEquals(FundingStatus.WAITING_GIFT_VERIFICATION, funding.status)
    }

    @Test
    fun `공개 상태가 아닌 펀딩은 선물 인증 대기로 전환할 수 없다`() {
        val birthday = funding(deadline = OPENED_AT.minusDays(1))

        val typeException = assertFailsWith<BusinessException> {
            birthday.beginGiftVerification()
        }
        assertEquals(FundingErrorCode.FUNDING_GIFT_VERIFICATION_NOT_COMPLETABLE, typeException.errorCode)

        val waitingReveal = funding(
            type = FundingType.SURPRISE,
            deadline = OPENED_AT.minusDays(1),
            revealAt = OPENED_AT.minusMinutes(1),
        )
        setField(waitingReveal, "status", FundingStatus.WAITING_REVEAL)
        val statusException = assertFailsWith<BusinessException> {
            waitingReveal.beginGiftVerification()
        }
        assertEquals(FundingErrorCode.FUNDING_GIFT_VERIFICATION_NOT_COMPLETABLE, statusException.errorCode)
    }

    @Test
    fun `마감된 생일 펀딩을 연장하면 진행 상태와 새 마감 정보를 기록한다`() {
        val funding = funding(deadline = OPENED_AT.minusDays(1))
        setField(funding, "status", FundingStatus.CLOSED)
        val newDeadline = OPENED_AT.plusDays(14)

        funding.extendDeadline(newDeadline, OPENED_AT, OPENED_AT, confirmedAmount = 80_000L)

        assertEquals(newDeadline, funding.deadline)
        assertEquals(1, funding.extensionCount)
        assertEquals(OPENED_AT, funding.extendedAt)
        assertEquals(FundingStatus.FUNDING, funding.status)
    }

    @Test
    fun `결과를 연 펀딩을 연장하면 롤링페이퍼는 유지할 수 있도록 결과 확정 상태만 초기화한다`() {
        val funding = funding(deadline = OPENED_AT.minusDays(1))
        funding.openResult(OPENED_AT)

        funding.extendDeadline(
            OPENED_AT.plusDays(7),
            requestedAt = OPENED_AT,
            extendedAt = OPENED_AT.plusHours(1),
            confirmedAmount = 80_000L,
        )

        assertEquals(null, funding.resultOpenedAt)
        assertEquals(FundingStatus.FUNDING, funding.status)
    }

    @Test
    fun `서프라이즈와 마감 전 생일 펀딩은 연장할 수 없다`() {
        val surprise = funding(
            type = FundingType.SURPRISE,
            deadline = OPENED_AT.minusDays(1),
            revealAt = OPENED_AT.plusDays(1),
        )
        val typeException = assertFailsWith<BusinessException> {
            surprise.extendDeadline(OPENED_AT.plusDays(1), OPENED_AT, OPENED_AT, confirmedAmount = 0L)
        }
        assertEquals(FundingErrorCode.BIRTHDAY_FUNDING_REQUIRED, typeException.errorCode)

        val ongoing = funding(deadline = OPENED_AT.plusMinutes(1))
        val deadlineException = assertFailsWith<BusinessException> {
            ongoing.extendDeadline(OPENED_AT.plusDays(1), OPENED_AT, OPENED_AT, confirmedAmount = 0L)
        }
        assertEquals(FundingErrorCode.FUNDING_EXTENSION_BEFORE_DEADLINE, deadlineException.errorCode)
    }

    @Test
    fun `연장 횟수를 모두 사용했거나 연장할 수 없는 상태이면 거부한다`() {
        val alreadyExtended = funding(deadline = OPENED_AT.minusDays(1))
        setField(alreadyExtended, "extensionCount", 1)
        val countException = assertFailsWith<BusinessException> {
            alreadyExtended.extendDeadline(OPENED_AT.plusDays(1), OPENED_AT, OPENED_AT, confirmedAmount = 0L)
        }
        assertEquals(FundingErrorCode.FUNDING_EXTENSION_LIMIT_EXCEEDED, countException.errorCode)

        val cancelled = funding(deadline = OPENED_AT.minusDays(1))
        setField(cancelled, "status", FundingStatus.CANCELLED)
        val statusException = assertFailsWith<BusinessException> {
            cancelled.extendDeadline(OPENED_AT.plusDays(1), OPENED_AT, OPENED_AT, confirmedAmount = 0L)
        }
        assertEquals(FundingErrorCode.FUNDING_EXTENSION_NOT_AVAILABLE, statusException.errorCode)
    }

    @Test
    fun `목표 금액을 달성했거나 새 마감일 범위를 벗어나면 연장할 수 없다`() {
        val targetReached = funding(deadline = OPENED_AT.minusDays(1))
        val amountException = assertFailsWith<BusinessException> {
            targetReached.extendDeadline(OPENED_AT.plusDays(1), OPENED_AT, OPENED_AT, confirmedAmount = 100_000L)
        }
        assertEquals(FundingErrorCode.FUNDING_TARGET_REACHED, amountException.errorCode)

        val invalidDeadline = funding(deadline = OPENED_AT.minusDays(1))
        val deadlineException = assertFailsWith<BusinessException> {
            invalidDeadline.extendDeadline(OPENED_AT, OPENED_AT, OPENED_AT, confirmedAmount = 0L)
        }
        assertEquals(FundingErrorCode.FUNDING_EXTENSION_DEADLINE_INVALID, deadlineException.errorCode)

        val tooLong = funding(deadline = OPENED_AT.minusDays(1))
        val periodException = assertFailsWith<BusinessException> {
            tooLong.extendDeadline(OPENED_AT.plusDays(15), OPENED_AT, OPENED_AT, confirmedAmount = 0L)
        }
        assertEquals(FundingErrorCode.FUNDING_EXTENSION_PERIOD_EXCEEDED, periodException.errorCode)
    }

    @Test
    fun `일반 펀딩을 조기 마감하면 최초 마감 시각과 상태를 유지한다`() {
        val funding = funding(deadline = OPENED_AT.plusDays(3))

        funding.closeEarly(OPENED_AT)
        funding.closeEarly(OPENED_AT.plusHours(1))

        assertEquals(FundingStatus.CLOSED, funding.status)
        assertEquals(OPENED_AT, funding.earlyClosedAt)
        assertEquals(OPENED_AT.plusDays(3), funding.deadline)
    }

    @Test
    fun `서프라이즈를 조기 마감하면 공개 시각을 유지하고 선물 준비 상태가 된다`() {
        val revealAt = OPENED_AT.plusDays(5)
        val funding = funding(
            type = FundingType.SURPRISE,
            deadline = OPENED_AT.plusDays(3),
            revealAt = revealAt,
        )

        funding.closeEarly(OPENED_AT)

        assertEquals(FundingStatus.GIFT_PREPARING, funding.status)
        assertEquals(revealAt, funding.revealAt)
    }

    @Test
    fun `마감된 서프라이즈의 선물 준비를 완료하면 공개 대기 상태가 되고 재요청은 최초 시각을 유지한다`() {
        val funding = funding(
            type = FundingType.SURPRISE,
            deadline = OPENED_AT,
            revealAt = OPENED_AT.plusDays(1),
        )
        setField(funding, "status", FundingStatus.CLOSED)

        funding.prepareSurpriseGift(OPENED_AT)
        funding.prepareSurpriseGift(OPENED_AT.plusHours(1))

        assertEquals(FundingStatus.WAITING_REVEAL, funding.status)
        assertEquals(OPENED_AT, funding.giftPreparedAt)
    }

    @Test
    fun `조기 마감한 서프라이즈는 기존 마감일 전에도 선물 준비를 완료할 수 있다`() {
        val funding = funding(
            type = FundingType.SURPRISE,
            deadline = OPENED_AT.plusDays(3),
            revealAt = OPENED_AT.plusDays(4),
        )
        funding.closeEarly(OPENED_AT)

        funding.prepareSurpriseGift(OPENED_AT.plusMinutes(1))

        assertEquals(FundingStatus.WAITING_REVEAL, funding.status)
    }

    @Test
    fun `마감 전이거나 생일 펀딩이거나 취소된 서프라이즈는 선물 준비를 완료할 수 없다`() {
        val beforeDeadline = funding(
            type = FundingType.SURPRISE,
            deadline = OPENED_AT.plusSeconds(1),
            revealAt = OPENED_AT.plusDays(1),
        )
        val notEnded = assertFailsWith<BusinessException> { beforeDeadline.prepareSurpriseGift(OPENED_AT) }
        assertEquals(FundingErrorCode.FUNDING_NOT_ENDED, notEnded.errorCode)

        val birthday = funding(deadline = OPENED_AT.minusDays(1))
        val notSurprise = assertFailsWith<BusinessException> { birthday.prepareSurpriseGift(OPENED_AT) }
        assertEquals(FundingErrorCode.SURPRISE_FUNDING_REQUIRED, notSurprise.errorCode)

        val cancelled = funding(
            type = FundingType.SURPRISE,
            deadline = OPENED_AT.minusDays(1),
            revealAt = OPENED_AT.minusHours(1),
        )
        setField(cancelled, "status", FundingStatus.CANCELLED)
        val notAvailable = assertFailsWith<BusinessException> { cancelled.prepareSurpriseGift(OPENED_AT) }
        assertEquals(FundingErrorCode.SURPRISE_GIFT_PREPARATION_NOT_AVAILABLE, notAvailable.errorCode)
    }

    @Test
    fun `준비 시각 없이 이미 공개된 서프라이즈의 선물 준비 재요청은 상태를 바꾸지 않는다`() {
        val revealed = funding(
            type = FundingType.SURPRISE,
            deadline = OPENED_AT.minusDays(1),
            revealAt = OPENED_AT.minusHours(1),
        )
        setField(revealed, "status", FundingStatus.REVEALED)

        revealed.prepareSurpriseGift(OPENED_AT)

        assertEquals(FundingStatus.REVEALED, revealed.status)
        assertNull(revealed.giftPreparedAt)
    }

    @Test
    fun `마감됐거나 완료된 펀딩은 조기 마감할 수 없다`() {
        val ended = funding(deadline = OPENED_AT)
        val deadlineException = assertFailsWith<BusinessException> { ended.closeEarly(OPENED_AT) }
        assertEquals(FundingErrorCode.FUNDING_EARLY_CLOSE_NOT_AVAILABLE, deadlineException.errorCode)

        val completed = funding(deadline = OPENED_AT.plusDays(1))
        setField(completed, "status", FundingStatus.COMPLETED)
        val statusException = assertFailsWith<BusinessException> { completed.closeEarly(OPENED_AT) }
        assertEquals(FundingErrorCode.FUNDING_EARLY_CLOSE_NOT_AVAILABLE, statusException.errorCode)
    }

    @Test
    fun `상품을 변경하면 스냅샷을 교체하고 변경 전후 이력을 만든다`() {
        val funding = funding(deadline = OPENED_AT.plusDays(3))
        setField(funding, "id", 12L)
        val changedProduct = FundingProductSnapshot(
            sourceWishId = 2L,
            name = "새 헤드폰",
            imageUrl = "https://image.example.com/new.jpg",
            price = 120_000L,
            url = "https://shop.example.com/new",
        )

        val history = checkNotNull(funding.changeProduct(changedProduct, OPENED_AT))

        assertEquals(changedProduct, funding.product)
        assertEquals(OPENED_AT, funding.productChangedAt)
        assertEquals(12L, history.fundingId)
        assertEquals(1L, history.previousSourceWishId)
        assertEquals("무선 헤드폰", history.previousProductName)
        assertEquals("https://image.example.com/product.jpg", history.previousProductImageUrl)
        assertEquals(100_000L, history.previousProductPrice)
        assertEquals("https://shop.example.com/product", history.previousProductUrl)
        assertEquals(2L, history.changedSourceWishId)
        assertEquals("새 헤드폰", history.changedProductName)
        assertEquals("https://image.example.com/new.jpg", history.changedProductImageUrl)
        assertEquals(120_000L, history.changedProductPrice)
        assertEquals("https://shop.example.com/new", history.changedProductUrl)
        assertEquals(OPENED_AT, history.changedAt)
        assertEquals(0L, history.id)
    }

    @Test
    fun `조기 마감 후에는 상품 변경과 기간 연장을 거부한다`() {
        val funding = funding(deadline = OPENED_AT.plusDays(3))
        funding.closeEarly(OPENED_AT)
        val changeException = assertFailsWith<BusinessException> {
            funding.changeProduct(
                FundingProductSnapshot(2L, "새 상품", "https://image.example.com/new.jpg", 1L, "https://shop.example.com/new"),
                OPENED_AT.plusMinutes(1),
            )
        }
        assertEquals(FundingErrorCode.FUNDING_PRODUCT_CHANGE_NOT_AVAILABLE, changeException.errorCode)

        val extensionException = assertFailsWith<BusinessException> {
            funding.extendDeadline(
                OPENED_AT.plusDays(5),
                requestedAt = OPENED_AT.plusDays(1),
                extendedAt = OPENED_AT.plusDays(1),
                confirmedAmount = 0L,
            )
        }
        assertEquals(FundingErrorCode.FUNDING_EXTENSION_NOT_AVAILABLE, extensionException.errorCode)
    }

    private fun funding(
        type: FundingType = FundingType.BIRTHDAY,
        deadline: LocalDateTime,
        revealAt: LocalDateTime? = null,
    ): Funding =
        Funding(
            ownerId = 1L,
            recipientId = if (type == FundingType.SURPRISE) 2L else null,
            type = type,
            title = "생일 선물 펀딩",
            targetAmount = 100_000L,
            deadline = deadline,
            revealAt = revealAt,
            story = null,
            promise = null,
            product = product(),
            payoutAccount = payoutAccount(),
        )

    private fun createBirthday(
        title: String = "생일 선물 펀딩",
        targetAmount: Long = 100_000L,
        deadline: LocalDateTime = OPENED_AT.plusDays(1),
        story: String = "새 헤드폰이 필요해요",
        promise: String? = null,
    ): Funding =
        Funding.createBirthday(
            ownerId = 1L,
            title = title,
            targetAmount = targetAmount,
            deadline = deadline,
            story = story,
            promise = promise,
            product = product(),
            payoutAccount = payoutAccount(),
            now = OPENED_AT,
        )

    private fun product(): FundingProductSnapshot =
        FundingProductSnapshot(
            sourceWishId = 1L,
            name = "무선 헤드폰",
            imageUrl = "https://image.example.com/product.jpg",
            price = 100_000L,
            url = "https://shop.example.com/product",
        )

    private fun payoutAccount(): FundingPayoutAccountSnapshot =
        FundingPayoutAccountSnapshot(
            sourcePayoutAccountId = 1L,
            bank = Bank.KAKAO_BANK,
            encryptedAccountNumber = "encrypted-account-number",
            accountHolder = "지유",
        )

    private fun setField(target: Any, fieldName: String, value: Any?) {
        target.javaClass.getDeclaredField(fieldName).apply {
            isAccessible = true
            set(target, value)
        }
    }

    companion object {
        private val OPENED_AT = LocalDateTime.of(2026, 11, 15, 0, 0)
    }
}
