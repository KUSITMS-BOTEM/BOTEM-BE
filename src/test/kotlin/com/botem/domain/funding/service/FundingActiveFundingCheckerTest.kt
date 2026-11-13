package com.botem.domain.funding.service

import com.botem.domain.funding.entity.FundingStatus
import com.botem.domain.funding.repository.FundingRepository
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FundingActiveFundingCheckerTest {

    private val fundingRepository = mock(FundingRepository::class.java)
    private val checker = FundingActiveFundingChecker(fundingRepository)

    @Test
    fun `완료와 취소를 제외한 소유 펀딩 존재 여부를 반환한다`() {
        `when`(fundingRepository.existsByOwnerIdAndStatusNotIn(USER_ID, ENDED_STATUSES))
            .thenReturn(true, false)

        assertTrue(checker.existsByUserId(USER_ID))
        assertFalse(checker.existsByUserId(USER_ID))
        verify(fundingRepository, org.mockito.Mockito.times(2))
            .existsByOwnerIdAndStatusNotIn(USER_ID, ENDED_STATUSES)
    }

    companion object {
        private const val USER_ID = 1L
        private val ENDED_STATUSES = setOf(
            FundingStatus.COMPLETED,
            FundingStatus.CANCELLED,
        )
    }
}
