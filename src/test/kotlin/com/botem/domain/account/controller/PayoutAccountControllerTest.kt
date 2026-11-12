package com.botem.domain.account.controller

import com.botem.domain.account.dto.request.SavePayoutAccountRequest
import com.botem.domain.account.dto.response.BankResponse
import com.botem.domain.account.dto.response.PayoutAccountResponse
import com.botem.domain.account.entity.AccountVerificationStatus
import com.botem.domain.account.entity.Bank
import com.botem.domain.account.service.PayoutAccountService
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(controllers = [PayoutAccountController::class])
class PayoutAccountControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var payoutAccountService: PayoutAccountService

    @Test
    fun `내 받을 계좌 목록을 조회한다`() {
        given(payoutAccountService.getPayoutAccounts(USER_ID)).willReturn(listOf(accountResponse(isDefault = true)))

        mockMvc.perform(get(BASE_URI).with(loggedIn()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data[0].id").value(ACCOUNT_ID))
            .andExpect(jsonPath("$.data[0].bankName").value("토스뱅크"))
            .andExpect(jsonPath("$.data[0].isDefault").value(true))
    }

    @Test
    fun `지원 은행 목록을 코드와 이름으로 조회한다`() {
        given(payoutAccountService.getSupportedBanks()).willReturn(listOf(BankResponse.from(Bank.TOSS_BANK)))

        mockMvc.perform(get("$BASE_URI/banks").with(loggedIn()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data[0].code").value("TOSS_BANK"))
            .andExpect(jsonPath("$.data[0].name").value("토스뱅크"))
    }

    @Test
    fun `받을 계좌를 등록하면 201을 반환한다`() {
        val request = SavePayoutAccountRequest(bank = Bank.TOSS_BANK, accountNumber = ACCOUNT_NUMBER)
        given(payoutAccountService.createPayoutAccount(USER_ID, request)).willReturn(accountResponse())

        mockMvc.perform(
            post(BASE_URI)
                .with(loggedIn())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{ "bank": "TOSS_BANK", "accountNumber": "$ACCOUNT_NUMBER" }"""),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.data.accountNumber").value(ACCOUNT_NUMBER))
    }

    @Test
    fun `계좌번호가 비어 있으면 400을 반환한다`() {
        mockMvc.perform(
            post(BASE_URI)
                .with(loggedIn())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{ "bank": "TOSS_BANK", "accountNumber": " " }"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("COMMON_001"))

        verifyNoInteractions(payoutAccountService)
    }

    @Test
    fun `받을 계좌를 수정한다`() {
        val request = SavePayoutAccountRequest(bank = Bank.TOSS_BANK, accountNumber = ACCOUNT_NUMBER)
        given(payoutAccountService.updatePayoutAccount(USER_ID, ACCOUNT_ID, request)).willReturn(accountResponse())

        mockMvc.perform(
            put("$BASE_URI/$ACCOUNT_ID")
                .with(loggedIn())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{ "bank": "TOSS_BANK", "accountNumber": "$ACCOUNT_NUMBER" }"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.id").value(ACCOUNT_ID))
    }

    @Test
    fun `기본 받을 계좌로 지정한다`() {
        given(payoutAccountService.designateDefaultAccount(USER_ID, ACCOUNT_ID))
            .willReturn(accountResponse(isDefault = true))

        mockMvc.perform(patch("$BASE_URI/$ACCOUNT_ID/default").with(loggedIn()).with(csrf()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.isDefault").value(true))
    }

    @Test
    fun `예상하지 못한 오류는 내부 메시지를 숨기고 500을 반환한다`() {
        given(payoutAccountService.getPayoutAccounts(USER_ID))
            .willThrow(IllegalStateException("jdbc:mysql://internal-db password=secret"))

        mockMvc.perform(get(BASE_URI).with(loggedIn()))
            .andExpect(status().isInternalServerError)
            .andExpect(jsonPath("$.error.code").value("COMMON_500"))
            .andExpect(jsonPath("$.error.detail").doesNotExist())
            .andExpect(content().string(not(containsString("secret"))))
    }

    private fun accountResponse(isDefault: Boolean = false): PayoutAccountResponse =
        PayoutAccountResponse(
            id = ACCOUNT_ID,
            bank = Bank.TOSS_BANK,
            bankName = "토스뱅크",
            accountNumber = ACCOUNT_NUMBER,
            accountHolder = null,
            isDefault = isDefault,
            verificationStatus = AccountVerificationStatus.UNVERIFIED,
        )

    private fun loggedIn() = authentication(UsernamePasswordAuthenticationToken(USER_ID, null, emptyList()))

    companion object {
        private const val BASE_URI = "/api/users/me/payout-accounts"
        private const val USER_ID = 1L
        private const val ACCOUNT_ID = 10L
        private const val ACCOUNT_NUMBER = "100159014821"
    }
}
