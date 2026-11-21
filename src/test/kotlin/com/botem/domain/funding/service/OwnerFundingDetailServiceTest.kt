package com.botem.domain.funding.service

import com.botem.domain.account.entity.Bank
import com.botem.domain.funding.dto.response.FundingManagementState
import com.botem.domain.funding.entity.Contribution
import com.botem.domain.funding.entity.ContributionStatus
import com.botem.domain.funding.entity.ContributionThankCard
import com.botem.domain.funding.entity.Funding
import com.botem.domain.funding.entity.FundingPayoutAccountSnapshot
import com.botem.domain.funding.entity.FundingProductSnapshot
import com.botem.domain.funding.entity.FundingStatus
import com.botem.domain.funding.entity.FundingType
import com.botem.domain.funding.exception.FundingErrorCode
import com.botem.domain.funding.repository.ContributionRepository
import com.botem.domain.funding.repository.ContributionThankCardRepository
import com.botem.domain.funding.repository.FundingRepository
import com.botem.domain.funding.repository.RollingPaperMessageRepository
import com.botem.domain.user.entity.User
import com.botem.domain.user.repository.UserRepository
import com.botem.global.entity.BaseEntity
import com.botem.global.exception.BusinessException
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyIterable
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OwnerFundingDetailServiceTest {

    private val fundingRepository = mock(FundingRepository::class.java)
    private val contributionRepository = mock(ContributionRepository::class.java)
    private val contributionThankCardRepository = mock(ContributionThankCardRepository::class.java)
    private val rollingPaperMessageRepository = mock(RollingPaperMessageRepository::class.java)
    private val userRepository = mock(UserRepository::class.java)
    private val service = OwnerFundingDetailService(
        fundingRepository = fundingRepository,
        contributionRepository = contributionRepository,
        contributionThankCardRepository = contributionThankCardRepository,
        rollingPaperMessageRepository = rollingPaperMessageRepository,
        userRepository = userRepository,
        clock = Clock.fixed(Instant.parse("2026-11-18T03:00:00Z"), ZoneId.of("Asia/Seoul")),
    )

    @Test
    fun `개설자가 아니면 조회할 수 없다`() {
        givenFunding(funding())

        val exception = assertFailsWith<BusinessException> { service.getManagementDetail(OTHER_USER_ID, FUNDING_ID) }

        assertEquals(FundingErrorCode.FUNDING_OWNER_ONLY, exception.errorCode)
    }

    @Test
    fun `취소된 펀딩은 찾을 수 없다`() {
        givenFunding(funding(status = FundingStatus.CANCELLED))

        val exception = assertFailsWith<BusinessException> { service.getManagementDetail(OWNER_ID, FUNDING_ID) }

        assertEquals(FundingErrorCode.FUNDING_NOT_FOUND, exception.errorCode)
    }

    @Test
    fun `참여 기록마다 한 줄씩 반환하고 같은 회원은 첫 참여 순서를 공유한다`() {
        givenFunding(funding())
        givenContributions(
            contribution(id = 1L, userId = MEMBER_ID, name = "하린", amount = 10_000L, confirmed = true),
            contribution(id = 2L, name = "도윤", amount = 0L, status = ContributionStatus.MESSAGE_ONLY),
            contribution(id = 3L, name = "취소", amount = 5_000L, status = ContributionStatus.CANCELLED),
            contribution(id = 4L, userId = MEMBER_ID, name = "하린", amount = 20_000L),
        )
        given(userRepository.findAllById(anyIterable()))
            .willReturn(listOf(User(kakaoId = 1L, nickname = "하린", profileImageUrl = PROFILE_URL).withId(MEMBER_ID)))

        val response = service.getManagementDetail(OWNER_ID, FUNDING_ID)

        assertEquals(listOf(1L, 2L, 4L), response.participants.map { it.contributionId })
        assertEquals(listOf(1, 2, 1), response.participants.map { it.order })
        assertEquals(
            listOf(ContributionStatus.CONFIRMED, ContributionStatus.MESSAGE_ONLY, ContributionStatus.AWAITING_TRANSFER),
            response.participants.map { it.status },
        )
        assertEquals(PROFILE_URL, response.participants[0].profileImageUrl)
        assertNull(response.participants[1].profileImageUrl)
        assertEquals(NOW.minusDays(1), response.participants[0].participatedAt)
        assertEquals(2, response.participantCount)
        assertEquals(FundingManagementState.IN_PROGRESS, response.managementState)
    }

    @Test
    fun `참여자별 감사카드 발송 여부와 발송 시각을 반환한다`() {
        givenFunding(funding())
        givenContributions(
            contribution(id = 1L, userId = MEMBER_ID, name = "하린", amount = 10_000L, confirmed = true),
            contribution(id = 2L, name = "도윤", amount = 10_000L, confirmed = true),
        )
        given(userRepository.findAllById(anyIterable())).willReturn(emptyList())
        given(contributionThankCardRepository.findAllByContributionIdIn(listOf(1L, 2L))).willReturn(
            listOf(
                ContributionThankCard(
                    contributionId = 1L,
                    senderUserId = OWNER_ID,
                    message = "고마워",
                    sentAt = NOW.minusHours(1),
                ),
            ),
        )

        val response = service.getManagementDetail(OWNER_ID, FUNDING_ID)

        assertTrue(response.participants[0].thankCardSent)
        assertEquals(NOW.minusHours(1), response.participants[0].thankCardSentAt)
        assertFalse(response.participants[1].thankCardSent)
        assertNull(response.participants[1].thankCardSentAt)
    }

    @Test
    fun `확정과 확인 대기 달성률을 따로 계산하고 메시지 전용 참여는 반영하지 않는다`() {
        givenFunding(funding())
        givenContributions(
            contribution(id = 1L, name = "하린", amount = 45_500L, confirmed = true),
            contribution(id = 2L, name = "도윤", amount = 10_000L, status = ContributionStatus.PENDING_CONFIRMATION),
            contribution(id = 3L, name = "민지", amount = 5_000L, status = ContributionStatus.PENDING_CONFIRMATION),
            contribution(id = 4L, name = "서준", amount = 0L, status = ContributionStatus.MESSAGE_ONLY),
        )
        given(rollingPaperMessageRepository.countByFundingId(FUNDING_ID)).willReturn(3L)

        val response = service.getManagementDetail(OWNER_ID, FUNDING_ID)

        assertEquals(45, response.confirmedPercentage)
        assertEquals(15, response.pendingPercentage)
        assertEquals(2, response.pendingContributionCount)
        assertEquals(3L, response.messageCount)
        assertEquals(3L, response.remainingDays)
        assertFalse(response.targetReached)
    }

    @Test
    fun `참여가 없으면 참여자 0명 상태이고 진행 중 관리 동작을 모두 허용한다`() {
        givenFunding(funding())
        givenContributions(contribution(id = 1L, name = "취소", amount = 5_000L, status = ContributionStatus.CANCELLED))

        val response = service.getManagementDetail(OWNER_ID, FUNDING_ID)

        assertEquals(FundingManagementState.NO_PARTICIPANTS, response.managementState)
        assertEquals(0, response.participantCount)
        assertTrue(response.actions.canCloseEarly)
        assertTrue(response.actions.canChangeProduct)
        assertTrue(response.actions.canReissueShareCard)
        assertFalse(response.actions.canExtend)
    }

    @Test
    fun `확정 금액이 목표에 도달하면 다 모인 상태다`() {
        givenFunding(funding())
        givenContributions(contribution(id = 1L, name = "하린", amount = TARGET_AMOUNT, confirmed = true))

        val response = service.getManagementDetail(OWNER_ID, FUNDING_ID)

        assertEquals(FundingManagementState.TARGET_REACHED, response.managementState)
        assertTrue(response.targetReached)
    }

    @Test
    fun `마감 후 목표에 못 미친 생일 펀딩은 한 번만 연장할 수 있고 진행 중 동작은 막는다`() {
        givenFunding(funding(deadline = NOW))
        givenContributions(contribution(id = 1L, name = "하린", amount = 10_000L, confirmed = true))

        val response = service.getManagementDetail(OWNER_ID, FUNDING_ID)

        assertEquals(FundingManagementState.CLOSED, response.managementState)
        assertNull(response.remainingDays)
        assertTrue(response.actions.canExtend)
        assertFalse(response.actions.canCloseEarly)
        assertFalse(response.actions.canChangeProduct)
        assertFalse(response.actions.canReissueShareCard)

        givenFunding(funding(deadline = NOW, extensionCount = 1))
        val extended = service.getManagementDetail(OWNER_ID, FUNDING_ID)
        assertTrue(extended.extended)
        assertFalse(extended.actions.canExtend)
    }

    @Test
    fun `마감 후 목표를 달성했거나 서프라이즈 펀딩이면 연장할 수 없다`() {
        givenFunding(funding(deadline = NOW))
        givenContributions(contribution(id = 1L, name = "하린", amount = TARGET_AMOUNT, confirmed = true))
        assertFalse(service.getManagementDetail(OWNER_ID, FUNDING_ID).actions.canExtend)

        givenFunding(funding(deadline = NOW, type = FundingType.SURPRISE))
        givenContributions(contribution(id = 1L, name = "하린", amount = 10_000L, confirmed = true))
        val surprise = service.getManagementDetail(OWNER_ID, FUNDING_ID)
        assertFalse(surprise.actions.canExtend)
        assertEquals(NOW.plusDays(1), surprise.revealAt)
    }

    @Test
    fun `서프라이즈 받는 사람이 탈퇴했으면 관리 상세에 표시한다`() {
        givenFunding(funding(deadline = NOW, type = FundingType.SURPRISE))
        givenContributions()
        val recipient = User(kakaoId = 7L, nickname = "서준", profileImageUrl = null, name = "서준")
        given(userRepository.findById(MEMBER_ID)).willReturn(Optional.of(recipient))
        assertFalse(service.getManagementDetail(OWNER_ID, FUNDING_ID).recipientWithdrawn)

        recipient.withdraw(NOW)
        assertTrue(service.getManagementDetail(OWNER_ID, FUNDING_ID).recipientWithdrawn)

        // 생일 펀딩은 받는 사람이 개설자 본인이므로 항상 false다
        givenFunding(funding(deadline = NOW))
        assertFalse(service.getManagementDetail(OWNER_ID, FUNDING_ID).recipientWithdrawn)
    }

    @Test
    fun `화면 제목용 주인공 이름은 생일 펀딩이면 개설자, 서프라이즈면 받는 사람이다`() {
        givenContributions()
        given(userRepository.findById(OWNER_ID)).willReturn(
            Optional.of(User(kakaoId = 1L, nickname = "지유닉", profileImageUrl = null, name = "지유")),
        )
        val recipient = User(kakaoId = 7L, nickname = "서준닉", profileImageUrl = null, name = null)
        given(userRepository.findById(MEMBER_ID)).willReturn(Optional.of(recipient))

        givenFunding(funding())
        assertEquals("지유", service.getManagementDetail(OWNER_ID, FUNDING_ID).celebrantName)

        // 실명이 없으면 닉네임, 탈퇴했으면 '탈퇴한 사용자'
        givenFunding(funding(type = FundingType.SURPRISE))
        assertEquals("서준닉", service.getManagementDetail(OWNER_ID, FUNDING_ID).celebrantName)

        recipient.withdraw(NOW)
        assertEquals("탈퇴한 사용자", service.getManagementDetail(OWNER_ID, FUNDING_ID).celebrantName)
    }

    @Test
    fun `조기 마감 후에는 관리 동작과 기간 연장을 허용하지 않는다`() {
        givenFunding(funding(deadline = NOW, earlyClosedAt = NOW.minusDays(1)))
        givenContributions()

        val response = service.getManagementDetail(OWNER_ID, FUNDING_ID)

        assertEquals(NOW.minusDays(1), response.earlyClosedAt)
        assertEquals(FundingManagementState.CLOSED, response.managementState)
        assertFalse(response.actions.canCloseEarly)
        assertFalse(response.actions.canChangeProduct)
        assertFalse(response.actions.canExtend)
    }

    @Test
    fun `원래 마감 전이어도 조기 마감한 펀딩은 관리 동작을 허용하지 않는다`() {
        givenFunding(funding(deadline = NOW.plusDays(1), earlyClosedAt = NOW.minusHours(1)))
        givenContributions()

        val response = service.getManagementDetail(OWNER_ID, FUNDING_ID)

        assertEquals(FundingManagementState.CLOSED, response.managementState)
        assertFalse(response.actions.canCloseEarly)
        assertFalse(response.actions.canChangeProduct)
        assertFalse(response.actions.canExtend)
    }

    @Test
    fun `상품 변경 여부와 마지막 변경 시각을 반환한다`() {
        givenFunding(funding(productChangedAt = NOW.minusHours(1)))
        givenContributions()

        val response = service.getManagementDetail(OWNER_ID, FUNDING_ID)

        assertTrue(response.productChanged)
        assertEquals(NOW.minusHours(1), response.productChangedAt)
    }

    private fun givenFunding(funding: Funding) {
        given(fundingRepository.findById(FUNDING_ID)).willReturn(Optional.of(funding))
    }

    private fun givenContributions(vararg contributions: Contribution) {
        given(contributionRepository.findAllByFundingIdOrderByCreatedAtAscIdAsc(FUNDING_ID))
            .willReturn(contributions.toList())
    }

    private fun funding(
        deadline: LocalDateTime = NOW.plusDays(3),
        status: FundingStatus = FundingStatus.FUNDING,
        type: FundingType = FundingType.BIRTHDAY,
        extensionCount: Int = 0,
        earlyClosedAt: LocalDateTime? = null,
        productChangedAt: LocalDateTime? = null,
    ): Funding =
        Funding(
            ownerId = OWNER_ID,
            recipientId = if (type == FundingType.SURPRISE) MEMBER_ID else null,
            type = type,
            title = "지유의 생일",
            targetAmount = TARGET_AMOUNT,
            deadline = deadline,
            revealAt = if (type == FundingType.SURPRISE) deadline.plusDays(1) else null,
            story = "새 헤드폰이 필요해요",
            promise = null,
            product = FundingProductSnapshot(
                sourceWishId = 1L,
                name = "소니 WH-1000XM5",
                imageUrl = "https://image.example.com/product.jpg",
                price = TARGET_AMOUNT,
                url = "https://shop.example.com/product",
            ),
            payoutAccount = FundingPayoutAccountSnapshot(
                sourcePayoutAccountId = 1L,
                bank = Bank.KAKAO_BANK,
                encryptedAccountNumber = "encrypted-account-number",
                accountHolder = "지유",
            ),
        ).also { funding ->
            setField(funding, Funding::class.java, "id", FUNDING_ID)
            setField(funding, Funding::class.java, "status", status)
            setField(funding, Funding::class.java, "extensionCount", extensionCount)
            earlyClosedAt?.let { setField(funding, Funding::class.java, "earlyClosedAt", it) }
            productChangedAt?.let { setField(funding, Funding::class.java, "productChangedAt", it) }
        }

    private fun contribution(
        id: Long,
        userId: Long? = null,
        name: String,
        amount: Long,
        status: ContributionStatus = ContributionStatus.AWAITING_TRANSFER,
        confirmed: Boolean = false,
    ): Contribution =
        Contribution(
            fundingId = FUNDING_ID,
            participantUserId = userId,
            participantName = name,
            amount = amount,
            status = status,
            guestPasswordHash = null,
        ).also { contribution ->
            if (confirmed) {
                contribution.markTransferSent()
                contribution.confirm(NOW)
            }
            setField(contribution, Contribution::class.java, "id", id)
            setField(contribution, BaseEntity::class.java, "createdAt", NOW.minusDays(1))
        }

    private fun User.withId(id: Long): User = also { setField(it, User::class.java, "id", id) }

    private fun setField(target: Any, owner: Class<*>, fieldName: String, value: Any) {
        owner.getDeclaredField(fieldName).apply {
            isAccessible = true
            set(target, value)
        }
    }

    companion object {
        private const val FUNDING_ID = 12L
        private const val OWNER_ID = 1L
        private const val OTHER_USER_ID = 2L
        private const val MEMBER_ID = 10L
        private const val TARGET_AMOUNT = 100_000L
        private const val PROFILE_URL = "https://image.example.com/profile.jpg"
        private val NOW = LocalDateTime.of(2026, 11, 18, 12, 0)
    }
}
