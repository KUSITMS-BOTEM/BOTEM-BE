package com.botem.domain.funding.controller

import com.botem.domain.funding.dto.request.CreateBirthdayFundingRequest
import com.botem.domain.funding.dto.response.CreateFundingResponse
import com.botem.domain.funding.dto.response.FundingDetailParticipantResponse
import com.botem.domain.funding.dto.response.FundingDetailProductResponse
import com.botem.domain.funding.dto.response.FundingDetailResponse
import com.botem.domain.funding.dto.response.FundingDisplayStatus
import com.botem.domain.funding.dto.response.FundingEarlyCloseResponse
import com.botem.domain.funding.dto.response.FundingExtensionResponse
import com.botem.domain.funding.dto.response.FundingManagementActionsResponse
import com.botem.domain.funding.dto.response.FundingManagementState
import com.botem.domain.funding.dto.response.FundingResultResponse
import com.botem.domain.funding.dto.response.FundingProductChangeResponse
import com.botem.domain.funding.dto.response.OwnerFundingDetailResponse
import com.botem.domain.funding.entity.FundingStatus
import com.botem.domain.funding.entity.FundingType
import com.botem.domain.funding.service.FundingCreationService
import com.botem.domain.funding.service.FundingDetailService
import com.botem.domain.funding.service.FundingEarlyCloseService
import com.botem.domain.funding.service.FundingExtensionService
import com.botem.domain.funding.service.FundingProductChangeService
import com.botem.domain.funding.service.FundingResultService
import com.botem.domain.funding.service.OwnerFundingDetailService
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

    @Test
    fun `로그인 사용자는 본인 ID로 펀딩 상세를 조회한다`() {
        given(fundingDetailService.getDetail(USER_ID, FUNDING_ID)).willReturn(detailResponse(ownerPreview = true))

        mockMvc.perform(
            get("/api/fundings/{fundingId}", FUNDING_ID)
                .with(authentication(UsernamePasswordAuthenticationToken(USER_ID, null, emptyList()))),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.fundingId").value(FUNDING_ID))
            .andExpect(jsonPath("$.data.displayStatus").value("IN_PROGRESS"))
            .andExpect(jsonPath("$.data.ownerPreview").value(true))
    }

    @Test
    fun `로그인 사용자가 일반 펀딩을 개설한다`() {
        val request = CreateBirthdayFundingRequest(
            wishId = 2L,
            payoutAccountId = null,
            title = "생일 선물 펀딩",
            targetAmount = 100_000L,
            deadline = LocalDate.of(2026, 11, 30),
            story = "새 헤드폰이 필요해요",
            promise = null,
        )
        given(fundingCreationService.createBirthdayFunding(USER_ID, request)).willReturn(
            CreateFundingResponse(FUNDING_ID, FundingType.BIRTHDAY, FundingStatus.FUNDING, "https://botem.me/fundings/12"),
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
                      "title": "생일 선물 펀딩",
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
            .andExpect(jsonPath("$.data.shareUrl").value("https://botem.me/fundings/12"))

        verify(fundingCreationService).createBirthdayFunding(USER_ID, request)
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

        verify(fundingResultService).openResult(USER_ID, FUNDING_ID, false)
    }

    @Test
    fun `로그인한 개설자가 펀딩 관리 상세를 조회한다`() {
        given(ownerFundingDetailService.getManagementDetail(USER_ID, FUNDING_ID)).willReturn(
            OwnerFundingDetailResponse(
                fundingId = FUNDING_ID,
                type = FundingType.BIRTHDAY,
                title = "생일 선물 펀딩",
                story = "새 헤드폰이 필요해요",
                promise = null,
                product = FundingDetailProductResponse(
                    name = "무선 헤드폰",
                    imageUrl = "https://image.example.com/product.jpg",
                    price = 100_000L,
                    url = "https://shop.example.com/product",
                ),
                productChanged = false,
                productChangedAt = null,
                targetAmount = 100_000L,
                deadline = LocalDateTime.of(2026, 11, 30, 23, 59, 59),
                revealAt = null,
                remainingDays = 3L,
                confirmedPercentage = 40,
                pendingPercentage = 10,
                pendingContributionCount = 1,
                targetReached = false,
                extended = false,
                earlyClosedAt = null,
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
            .andExpect(jsonPath("$.data.managementState").value("NO_PARTICIPANTS"))
            .andExpect(jsonPath("$.data.pendingContributionCount").value(1))
            .andExpect(jsonPath("$.data.actions.canCloseEarly").value(true))
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
            type = FundingType.BIRTHDAY,
            title = "생일 선물 펀딩",
            product = FundingDetailProductResponse(
                name = "무선 헤드폰",
                imageUrl = "https://image.example.com/product.jpg",
                price = 100_000L,
                url = "https://shop.example.com/product",
            ),
            productChanged = false,
            productChangedAt = null,
            story = "새 헤드폰이 필요해요",
            promise = null,
            deadline = LocalDateTime.of(2026, 11, 30, 23, 59, 59),
            remainingDays = 3L,
            confirmedPercentage = 40,
            pendingPercentage = 10,
            participantCount = 1,
            participants = listOf(FundingDetailParticipantResponse(order = 1, name = "지유", profileImageUrl = null)),
            displayStatus = FundingDisplayStatus.IN_PROGRESS,
            canContributeAmount = true,
            canParticipate = true,
            ownerPreview = ownerPreview,
            giftVerification = null,
            thankCard = null,
        )

    private fun resultResponse(resultOpenedAt: LocalDateTime?): FundingResultResponse =
        FundingResultResponse(
            fundingId = FUNDING_ID,
            productName = "무선 헤드폰",
            productImageUrl = "https://image.example.com/product.jpg",
            targetAmount = 100_000L,
            confirmedAmount = 80_000L,
            achievementPercentage = 80,
            targetReached = false,
            participantCount = 4L,
            messageCount = 3L,
            pendingContributionCount = 0L,
            pendingCleanupRequired = false,
            resultOpenedAt = resultOpenedAt,
        )

    companion object {
        private const val USER_ID = 1L
        private const val FUNDING_ID = 12L
        private val OPENED_AT = LocalDateTime.of(2026, 11, 15, 0, 0)
    }
}
