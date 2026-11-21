package com.botem.domain.funding.controller

import org.junit.jupiter.api.BeforeEach
import com.botem.domain.funding.service.FundingShareKeyResolver
import com.botem.domain.account.entity.Bank
import com.botem.domain.funding.dto.request.CreateBirthdayFundingRequest
import com.botem.domain.funding.dto.request.CreateSurpriseFundingRequest
import com.botem.domain.funding.dto.response.CreateFundingResponse
import com.botem.domain.funding.dto.response.FundingDetailParticipantResponse
import com.botem.domain.funding.dto.response.FundingDetailProductResponse
import com.botem.domain.funding.dto.response.FundingDetailResponse
import com.botem.domain.funding.dto.response.FundingDisplayStatus
import com.botem.domain.funding.dto.response.FundingEarlyCloseResponse
import com.botem.domain.funding.dto.response.FundingExtensionResponse
import com.botem.domain.funding.dto.response.FundingManagementActionsResponse
import com.botem.domain.funding.dto.response.FundingManagementState
import com.botem.domain.funding.dto.response.FundingResultContributionResponse
import com.botem.domain.funding.dto.response.FundingResultResponse
import com.botem.domain.funding.dto.response.GiftChoiceResponse
import com.botem.domain.funding.dto.response.FundingProductChangeResponse
import com.botem.domain.funding.dto.response.OwnerFundingDetailResponse
import com.botem.domain.funding.dto.response.SurpriseGiftPreparationResponse
import com.botem.domain.funding.entity.ContributionStatus
import com.botem.domain.funding.entity.FundingStatus
import com.botem.domain.funding.entity.FundingType
import com.botem.domain.funding.service.FundingCreationService
import com.botem.domain.funding.service.FundingDetailService
import com.botem.domain.funding.service.FundingEarlyCloseService
import com.botem.domain.funding.service.FundingExtensionService
import com.botem.domain.funding.service.FundingProductChangeService
import com.botem.domain.funding.service.FundingResultService
import com.botem.domain.funding.service.GiftChoiceService
import com.botem.domain.funding.service.OwnerFundingDetailService
import com.botem.domain.funding.service.SurpriseGiftPreparationService
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate
import java.time.LocalDateTime

@WebMvcTest(controllers = [FundingController::class])
class FundingControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean

    private lateinit var fundingShareKeyResolver: FundingShareKeyResolver


    @MockitoBean
    private lateinit var fundingCreationService: FundingCreationService

    @MockitoBean
    private lateinit var fundingResultService: FundingResultService

    @MockitoBean
    private lateinit var fundingDetailService: FundingDetailService

    @MockitoBean
    private lateinit var ownerFundingDetailService: OwnerFundingDetailService

    @MockitoBean
    private lateinit var fundingExtensionService: FundingExtensionService

    @MockitoBean
    private lateinit var fundingEarlyCloseService: FundingEarlyCloseService

    @MockitoBean
    private lateinit var fundingProductChangeService: FundingProductChangeService

    @MockitoBean
    private lateinit var surpriseGiftPreparationService: SurpriseGiftPreparationService

    @MockitoBean
    private lateinit var giftChoiceService: GiftChoiceService

    @BeforeEach

    fun stubShareKey() {

        given(fundingShareKeyResolver.fundingIdOf(SHARE_KEY)).willReturn(FUNDING_ID)

    }


    @Test
    fun `로그인 사용자는 본인 ID로 펀딩 상세를 조회한다`() {
        given(fundingDetailService.getDetail(USER_ID, FUNDING_ID)).willReturn(detailResponse(ownerPreview = true))

        mockMvc.perform(
            get("/api/fundings/share/{shareKey}", SHARE_KEY)
                .with(authentication(UsernamePasswordAuthenticationToken(USER_ID, null, emptyList()))),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.fundingId").value(FUNDING_ID))
            .andExpect(jsonPath("$.data.displayStatus").value("IN_PROGRESS"))
            .andExpect(jsonPath("$.data.ownerPreview").value(true))
            .andExpect(jsonPath("$.data.celebrantName").value("지유"))
            .andExpect(jsonPath("$.data.targetAmount").value(100_000))
            .andExpect(jsonPath("$.data.product.sellerName").value("BOTEM 브랜드샵"))
            .andExpect(jsonPath("$.data.participants[0].contributedDate").value("2026-11-12"))
    }

    @Test
    fun `로그인 사용자가 일반 펀딩을 개설한다`() {
        val request = CreateBirthdayFundingRequest(
            wishId = 2L,
            bank = Bank.TOSS_BANK,
            accountNumber = "100159014821",
            targetAmount = 100_000L,
            deadline = LocalDate.of(2026, 11, 30),
            story = "새 헤드폰이 필요해요",
            promise = null,
        )
        given(fundingCreationService.createBirthdayFunding(USER_ID, request)).willReturn(
            CreateFundingResponse(
                FUNDING_ID,
                "0123456789abcdef0123456789abcdef",
                FundingType.BIRTHDAY,
                FundingStatus.FUNDING,
                "https://botem.me/share/0123456789abcdef0123456789abcdef",
                LocalDateTime.of(2026, 11, 30, 23, 59, 59),
                null,
            ),
        )

        mockMvc.perform(
            post("/api/fundings")
                .with(authentication(UsernamePasswordAuthenticationToken(USER_ID, null, emptyList())))
                .with(csrf())
                .contentType("application/json")
                .content(
                    """
                    {
                      "wishId": 2,
                      "bank": "TOSS_BANK",
                      "accountNumber": "100159014821",
                      "targetAmount": 100000,
                      "deadline": "2026-11-30",
                      "story": "새 헤드폰이 필요해요"
                    }
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.data.fundingId").value(FUNDING_ID))
            .andExpect(jsonPath("$.data.type").value("BIRTHDAY"))
            .andExpect(jsonPath("$.data.status").value("FUNDING"))
            .andExpect(jsonPath("$.data.shareUrl").value("https://botem.me/share/0123456789abcdef0123456789abcdef"))

        verify(fundingCreationService).createBirthdayFunding(USER_ID, request)
    }

    @Test
    fun `로그인 사용자가 서프라이즈 펀딩을 개설하고 비밀 노출 옵션은 생략하면 true로 받는다`() {
        val request = CreateSurpriseFundingRequest(
            recipientId = 5L,
            wishId = 2L,
            bank = Bank.TOSS_BANK,
            accountNumber = "1001-5901-4821",
            targetAmount = 39_000L,
        )
        given(fundingCreationService.createSurpriseFunding(USER_ID, request)).willReturn(
            CreateFundingResponse(
                FUNDING_ID,
                "0123456789abcdef0123456789abcdef",
                FundingType.SURPRISE,
                FundingStatus.FUNDING,
                "https://botem.me/share/0123456789abcdef0123456789abcdef",
                LocalDateTime.of(2026, 12, 12, 23, 59, 59),
                LocalDateTime.of(2026, 12, 13, 0, 0),
            ),
        )

        mockMvc.perform(
            post("/api/fundings/surprise")
                .with(authentication(UsernamePasswordAuthenticationToken(USER_ID, null, emptyList())))
                .with(csrf())
                .contentType("application/json")
                .content(
                    """
                    {
                      "recipientId": 5,
                      "wishId": 2,
                      "bank": "TOSS_BANK",
                      "accountNumber": "1001-5901-4821",
                      "targetAmount": 39000
                    }
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.data.type").value("SURPRISE"))
            .andExpect(jsonPath("$.data.shareUrl").value("https://botem.me/share/0123456789abcdef0123456789abcdef"))
            .andExpect(jsonPath("$.data.revealAt").value("2026-12-13T00:00:00"))

        verify(fundingCreationService).createSurpriseFunding(USER_ID, request)
    }

    @Test
    fun `서프라이즈 펀딩 개설 요청의 값이나 계좌가 올바르지 않으면 400을 반환한다`() {
        listOf(
            """"recipientId": 0, "bank": "TOSS_BANK", "accountNumber": "100159014821", "targetAmount": 10000001""",
            """"recipientId": 5, "accountNumber": "100159014821", "targetAmount": 39000""",
            """"recipientId": 5, "bank": "TOSS_BANK", "accountNumber": " ", "targetAmount": 39000""",
        ).forEach { fields ->
            mockMvc.perform(
                post("/api/fundings/surprise")
                    .with(authentication(UsernamePasswordAuthenticationToken(USER_ID, null, emptyList())))
                    .with(csrf())
                    .contentType("application/json")
                    .content("""{ $fields, "wishId": 2 }"""),
            )
                .andExpect(status().isBadRequest)
        }

        verifyNoInteractions(fundingCreationService)
    }

    @Test
    fun `일반 펀딩 개설 요청의 필수 값이 올바르지 않으면 400을 반환한다`() {
        mockMvc.perform(
            post("/api/fundings")
                .with(authentication(UsernamePasswordAuthenticationToken(USER_ID, null, emptyList())))
                .with(csrf())
                .contentType("application/json")
                .content(
                    """
                    {
                      "wishId": 0,
                      "title": " ",
                      "targetAmount": 10000001,
                      "deadline": "2026-11-30",
                      "story": " "
                    }
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isBadRequest)

        verifyNoInteractions(fundingCreationService)
    }

    @Test
    fun `일반 펀딩 개설 요청에 은행이나 계좌번호가 없으면 400을 반환한다`() {
        listOf(
            """"accountNumber": "100159014821"""",
            """"bank": "TOSS_BANK", "accountNumber": " """",
        ).forEach { account ->
            mockMvc.perform(
                post("/api/fundings")
                    .with(authentication(UsernamePasswordAuthenticationToken(USER_ID, null, emptyList())))
                    .with(csrf())
                    .contentType("application/json")
                    .content(
                        """
                        {
                          "wishId": 2,
                          $account,
                          "targetAmount": 100000,
                          "deadline": "2026-11-30",
                          "story": "새 헤드폰이 필요해요"
                        }
                        """.trimIndent(),
                    ),
            )
                .andExpect(status().isBadRequest)
        }

        verifyNoInteractions(fundingCreationService)
    }

    @Test
    fun `로그인한 개설자가 펀딩 결과를 조회한다`() {
        given(fundingResultService.getResult(USER_ID, FUNDING_ID)).willReturn(resultResponse(resultOpenedAt = null))

        mockMvc.perform(
            get("/api/fundings/{fundingId}/result", FUNDING_ID)
                .with(authentication(UsernamePasswordAuthenticationToken(USER_ID, null, emptyList()))),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.fundingId").value(FUNDING_ID))
            .andExpect(jsonPath("$.data.confirmedAmount").value(80_000L))
            .andExpect(jsonPath("$.data.achievementPercentage").value(80))
            .andExpect(jsonPath("$.data.targetReached").value(false))
            .andExpect(jsonPath("$.data.participantCount").value(4L))
            .andExpect(jsonPath("$.data.resultOpenedAt").isEmpty)

        verify(fundingResultService).getResult(USER_ID, FUNDING_ID)
    }

    @Test
    fun `로그인한 개설자가 펀딩 결과를 팡 연다`() {
        given(fundingResultService.openResult(USER_ID, FUNDING_ID, false)).willReturn(resultResponse(OPENED_AT))

        mockMvc.perform(
            post("/api/fundings/{fundingId}/result/open", FUNDING_ID)
                .with(authentication(UsernamePasswordAuthenticationToken(USER_ID, null, emptyList())))
                .with(csrf()),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.fundingId").value(FUNDING_ID))
            .andExpect(jsonPath("$.data.resultOpenedAt").value("2026-11-15T00:00:00"))
            .andExpect(jsonPath("$.data.contributions[0].contributionId").value(31))
            .andExpect(jsonPath("$.data.contributions[0].hasMessage").value(true))

        verify(fundingResultService).openResult(USER_ID, FUNDING_ID, false)
    }

    @Test
    fun `로그인한 개설자가 구매할 선물을 정한다`() {
        given(giftChoiceService.choose(USER_ID, FUNDING_ID)).willReturn(
            GiftChoiceResponse(
                fundingId = FUNDING_ID,
                giftVerificationId = 7L,
                productName = "무선 헤드폰",
                productImageUrl = "https://image.example.com/product.jpg",
                productUrl = "https://shop.example.com/product",
            ),
        )

        mockMvc.perform(
            post("/api/fundings/{fundingId}/gift-choice", FUNDING_ID)
                .with(authentication(UsernamePasswordAuthenticationToken(USER_ID, null, emptyList())))
                .with(csrf()),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.giftVerificationId").value(7))
            .andExpect(jsonPath("$.data.productUrl").value("https://shop.example.com/product"))

        verify(giftChoiceService).choose(USER_ID, FUNDING_ID)
    }

    @Test
    fun `로그인한 주최자가 서프라이즈 선물 준비 화면을 조회한다`() {
        given(surpriseGiftPreparationService.getPreparation(USER_ID, FUNDING_ID))
            .willReturn(surpriseGiftPreparationResponse(giftPreparedAt = null))

        mockMvc.perform(
            get("/api/fundings/{fundingId}/surprise/gift-preparation", FUNDING_ID)
                .with(authentication(UsernamePasswordAuthenticationToken(USER_ID, null, emptyList()))),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.confirmedAmount").value(80_000))
            .andExpect(jsonPath("$.data.productSellerName").value("BOTEM 브랜드샵"))
            .andExpect(jsonPath("$.data.giftPreparedAt").isEmpty)

        verify(surpriseGiftPreparationService).getPreparation(USER_ID, FUNDING_ID)
    }

    @Test
    fun `로그인한 주최자가 확인 대기를 제외하고 서프라이즈 선물 준비를 완료한다`() {
        given(surpriseGiftPreparationService.prepare(USER_ID, FUNDING_ID, true))
            .willReturn(surpriseGiftPreparationResponse(giftPreparedAt = OPENED_AT))

        mockMvc.perform(
            post("/api/fundings/{fundingId}/surprise/gift-preparation/complete", FUNDING_ID)
                .param("excludePendingContributions", "true")
                .with(authentication(UsernamePasswordAuthenticationToken(USER_ID, null, emptyList())))
                .with(csrf()),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.recipientName").value("지유"))
            .andExpect(jsonPath("$.data.productSellerName").value("BOTEM 브랜드샵"))
            .andExpect(jsonPath("$.data.giftPreparedAt").value("2026-11-15T00:00:00"))

        verify(surpriseGiftPreparationService).prepare(USER_ID, FUNDING_ID, true)
    }

    @Test
    fun `로그인한 개설자가 펀딩 관리 상세를 조회한다`() {
        given(ownerFundingDetailService.getManagementDetail(USER_ID, FUNDING_ID)).willReturn(
            OwnerFundingDetailResponse(
                fundingId = FUNDING_ID,
                shareKey = "0123456789abcdef0123456789abcdef",
                type = FundingType.BIRTHDAY,
                title = "생일 선물 펀딩",
                celebrantName = "지유",
                story = "새 헤드폰이 필요해요",
                promise = null,
                product = FundingDetailProductResponse(
                    name = "무선 헤드폰",
                    imageUrl = "https://image.example.com/product.jpg",
                    price = 100_000L,
                    url = "https://shop.example.com/product",
                    sellerName = "BOTEM 브랜드샵",
                ),
                productChanged = false,
                productChangedAt = null,
                targetAmount = 100_000L,
                deadline = LocalDateTime.of(2026, 11, 30, 23, 59, 59),
                revealAt = null,
                recipientWithdrawn = false,
                remainingDays = 3L,
                confirmedPercentage = 40,
                pendingPercentage = 10,
                pendingContributionCount = 1,
                targetReached = false,
                extended = false,
                earlyClosedAt = null,
                giftPreparedAt = null,
                participantCount = 0,
                participants = emptyList(),
                messageCount = 0L,
                managementState = FundingManagementState.NO_PARTICIPANTS,
                actions = FundingManagementActionsResponse(
                    canCloseEarly = true,
                    canExtend = false,
                    canChangeProduct = true,
                    canReissueShareCard = true,
                ),
            ),
        )

        mockMvc.perform(
            get("/api/fundings/{fundingId}/management", FUNDING_ID)
                .with(authentication(UsernamePasswordAuthenticationToken(USER_ID, null, emptyList()))),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.celebrantName").value("지유"))
            .andExpect(jsonPath("$.data.managementState").value("NO_PARTICIPANTS"))
            .andExpect(jsonPath("$.data.pendingContributionCount").value(1))
            .andExpect(jsonPath("$.data.actions.canCloseEarly").value(true))
            .andExpect(jsonPath("$.data.product.sellerName").value("BOTEM 브랜드샵"))
    }

    @Test
    fun `로그인한 개설자가 펀딩 기간을 연장한다`() {
        val deadline = LocalDateTime.of(2026, 11, 30, 23, 59, 59, 999_999_000)
        given(fundingExtensionService.extend(USER_ID, FUNDING_ID, deadline)).willReturn(
            FundingExtensionResponse(
                fundingId = FUNDING_ID,
                status = FundingStatus.FUNDING,
                deadline = deadline,
                extensionCount = 1,
                extendedAt = LocalDateTime.of(2026, 11, 18, 12, 0),
            ),
        )

        mockMvc.perform(
            post("/api/fundings/{fundingId}/extension", FUNDING_ID)
                .with(authentication(UsernamePasswordAuthenticationToken(USER_ID, null, emptyList())))
                .with(csrf())
                .contentType("application/json")
                .content("""{ "newDeadline": "2026-11-30" }"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.fundingId").value(FUNDING_ID))
            .andExpect(jsonPath("$.data.status").value("FUNDING"))
            .andExpect(jsonPath("$.data.deadline").value("2026-11-30T23:59:59.999999"))
            .andExpect(jsonPath("$.data.extensionCount").value(1))

        verify(fundingExtensionService).extend(USER_ID, FUNDING_ID, deadline)
    }

    @Test
    fun `로그인한 개설자가 펀딩을 조기 마감한다`() {
        given(fundingEarlyCloseService.close(USER_ID, FUNDING_ID)).willReturn(
            FundingEarlyCloseResponse(
                fundingId = FUNDING_ID,
                type = FundingType.BIRTHDAY,
                status = FundingStatus.CLOSED,
                deadline = LocalDateTime.of(2026, 11, 30, 23, 59, 59),
                revealAt = null,
                earlyClosedAt = LocalDateTime.of(2026, 11, 18, 12, 0),
            ),
        )

        mockMvc.perform(
            post("/api/fundings/{fundingId}/close", FUNDING_ID)
                .with(authentication(UsernamePasswordAuthenticationToken(USER_ID, null, emptyList())))
                .with(csrf()),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status").value("CLOSED"))
            .andExpect(jsonPath("$.data.earlyClosedAt").value("2026-11-18T12:00:00"))

        verify(fundingEarlyCloseService).close(USER_ID, FUNDING_ID)
    }

    @Test
    fun `로그인한 개설자가 펀딩 상품을 변경한다`() {
        val product = FundingDetailProductResponse(
            name = "새 헤드폰",
            imageUrl = "https://image.example.com/new-product.jpg",
            price = 120_000L,
            url = "https://shop.example.com/new-product",
            sellerName = null,
        )
        given(fundingProductChangeService.change(USER_ID, FUNDING_ID, 7L)).willReturn(
            FundingProductChangeResponse(
                fundingId = FUNDING_ID,
                product = product,
                productChangedAt = LocalDateTime.of(2026, 11, 18, 12, 0),
            ),
        )

        mockMvc.perform(
            patch("/api/fundings/{fundingId}/product", FUNDING_ID)
                .with(authentication(UsernamePasswordAuthenticationToken(USER_ID, null, emptyList())))
                .with(csrf())
                .contentType("application/json")
                .content("""{ "wishId": 7 }"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.product.name").value("새 헤드폰"))
            // 추천 상품이 아닌 위시로 바꾸면 판매자명이 없어도 필드는 null로 내려준다
            .andExpect(jsonPath("$.data.product.sellerName").value(nullValue()))
            .andExpect(jsonPath("$.data.productChangedAt").value("2026-11-18T12:00:00"))

        verify(fundingProductChangeService).change(USER_ID, FUNDING_ID, 7L)
    }

    @Test
    fun `상품 변경 위시 ID가 양수가 아니면 400을 반환한다`() {
        mockMvc.perform(
            patch("/api/fundings/{fundingId}/product", FUNDING_ID)
                .with(authentication(UsernamePasswordAuthenticationToken(USER_ID, null, emptyList())))
                .with(csrf())
                .contentType("application/json")
                .content("""{ "wishId": 0 }"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("COMMON_001"))
            .andExpect(jsonPath("$.error.detail").value("wishId: 0보다 커야 합니다"))

        verifyNoInteractions(fundingProductChangeService)
    }

    @Test
    fun `인증 없이 펀딩 관리 상세를 조회하면 401을 반환한다`() {
        mockMvc.perform(get("/api/fundings/{fundingId}/management", FUNDING_ID))
            .andExpect(status().isUnauthorized)

        verifyNoInteractions(ownerFundingDetailService)
    }

    @Test
    fun `인증 없이 펀딩 결과를 조회하면 401을 반환한다`() {
        mockMvc.perform(get("/api/fundings/{fundingId}/result", FUNDING_ID))
            .andExpect(status().isUnauthorized)

        verifyNoInteractions(fundingResultService)
    }

    private fun detailResponse(ownerPreview: Boolean): FundingDetailResponse =
        FundingDetailResponse(
            fundingId = FUNDING_ID,
            shareKey = "0123456789abcdef0123456789abcdef",
            type = FundingType.BIRTHDAY,
            title = "생일 선물 펀딩",
            celebrantName = "지유",
            celebrantProfileImageUrl = null,
            targetAmount = 100_000L,
            product = FundingDetailProductResponse(
                name = "무선 헤드폰",
                imageUrl = "https://image.example.com/product.jpg",
                price = 100_000L,
                url = "https://shop.example.com/product",
                sellerName = "BOTEM 브랜드샵",
            ),
            productChanged = false,
            productChangedAt = null,
            story = "새 헤드폰이 필요해요",
            promise = null,
            deadline = LocalDateTime.of(2026, 11, 30, 23, 59, 59),
            extended = false,
            remainingDays = 3L,
            confirmedPercentage = 40,
            pendingPercentage = 10,
            participantCount = 1,
            participants = listOf(
                FundingDetailParticipantResponse(
                    order = 1,
                    name = "지유",
                    profileImageUrl = null,
                    contributedDate = LocalDate.of(2026, 11, 12),
                ),
            ),
            displayStatus = FundingDisplayStatus.IN_PROGRESS,
            canContributeAmount = true,
            canParticipate = true,
            ownerPreview = ownerPreview,
            giftVerification = null,
        )

    private fun resultResponse(resultOpenedAt: LocalDateTime?): FundingResultResponse =
        FundingResultResponse(
            fundingId = FUNDING_ID,
            productName = "무선 헤드폰",
            productImageUrl = "https://image.example.com/product.jpg",
            productUrl = "https://shop.example.com/product",
            productChanged = false,
            targetAmount = 100_000L,
            confirmedAmount = 80_000L,
            achievementPercentage = 80,
            targetReached = false,
            participantCount = 4L,
            messageCount = 3L,
            pendingContributionCount = 0L,
            pendingCleanupRequired = false,
            resultOpenedAt = resultOpenedAt,
            canExtend = false,
            giftChosen = false,
            contributions = listOf(
                FundingResultContributionResponse(
                    contributionId = 31L,
                    name = "서준",
                    profileImageUrl = null,
                    status = ContributionStatus.CONFIRMED,
                    participatedAt = LocalDateTime.of(2026, 11, 10, 14, 20),
                    hasMessage = true,
                    hasPhoto = false,
                    hasVoice = false,
                    thankCardSent = false,
                ),
            ),
        )

    private fun surpriseGiftPreparationResponse(giftPreparedAt: LocalDateTime?): SurpriseGiftPreparationResponse =
        SurpriseGiftPreparationResponse(
            fundingId = FUNDING_ID,
            shareKey = "0123456789abcdef0123456789abcdef",
            recipientName = "지유",
            recipientWithdrawn = false,
            productName = "무선 헤드폰",
            productImageUrl = "https://image.example.com/product.jpg",
            productUrl = "https://shop.example.com/product",
            productSellerName = "BOTEM 브랜드샵",
            targetAmount = 100_000L,
            confirmedAmount = 80_000L,
            achievementPercentage = 80,
            targetReached = false,
            participantCount = 4L,
            messageCount = 3L,
            pendingContributionCount = 0L,
            pendingCleanupRequired = false,
            fundingStartedAt = OPENED_AT.minusDays(10),
            deadline = OPENED_AT.minusHours(1),
            earlyClosedAt = null,
            revealAt = OPENED_AT.plusDays(1),
            giftPreparedAt = giftPreparedAt,
        )

    companion object {
        private const val SHARE_KEY = "0123456789abcdef0123456789abcdef"
        private const val USER_ID = 1L
        private const val FUNDING_ID = 12L
        private val OPENED_AT = LocalDateTime.of(2026, 11, 15, 0, 0)
    }
}
