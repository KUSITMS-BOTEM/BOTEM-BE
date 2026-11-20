package com.botem.domain.funding.service

import com.botem.domain.funding.exception.FundingErrorCode
import com.botem.domain.funding.repository.FundingRepository
import com.botem.global.exception.BusinessException
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FundingShareKeyResolverTest {

    private val fundingRepository = mock(FundingRepository::class.java)
    private val resolver = FundingShareKeyResolver(fundingRepository)

    @Test
    fun `공유 키로 펀딩 ID를 찾는다`() {
        given(fundingRepository.findIdByShareKey(SHARE_KEY)).willReturn(FUNDING_ID)

        assertEquals(FUNDING_ID, resolver.fundingIdOf(SHARE_KEY))
    }

    @Test
    fun `없는 공유 키는 펀딩 없음으로 응답한다`() {
        given(fundingRepository.findIdByShareKey(SHARE_KEY)).willReturn(null)

        val exception = assertFailsWith<BusinessException> { resolver.fundingIdOf(SHARE_KEY) }

        assertEquals(FundingErrorCode.FUNDING_NOT_FOUND, exception.errorCode)
    }

    @Test
    fun `순차 ID나 형식이 틀린 키는 조회하지 않고 펀딩 없음으로 응답한다`() {
        listOf("12", "0123456789ABCDEF0123456789ABCDEF", "${SHARE_KEY}0").forEach { invalidKey ->
            val exception = assertFailsWith<BusinessException> { resolver.fundingIdOf(invalidKey) }

            assertEquals(FundingErrorCode.FUNDING_NOT_FOUND, exception.errorCode)
        }
        verify(fundingRepository, never()).findIdByShareKey(anyString())
    }

    companion object {
        private const val FUNDING_ID = 10L
        private const val SHARE_KEY = "0123456789abcdef0123456789abcdef"
    }
}
