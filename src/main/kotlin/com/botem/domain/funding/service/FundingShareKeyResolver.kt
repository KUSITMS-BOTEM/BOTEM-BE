package com.botem.domain.funding.service

import com.botem.domain.funding.entity.Funding
import com.botem.domain.funding.exception.FundingErrorCode
import com.botem.domain.funding.repository.FundingRepository
import com.botem.global.exception.BusinessException
import org.springframework.stereotype.Component

// 공유 화면(참여자) API는 순차 ID 대신 공유 키로 펀딩을 찾는다. 없는 키와 형식이 틀린 키는 모두 '펀딩 없음'으로 응답한다
@Component
class FundingShareKeyResolver(
    private val fundingRepository: FundingRepository,
) {

    fun fundingIdOf(shareKey: String): Long =
        shareKey.takeIf(Funding::isShareKeyFormat)
            ?.let(fundingRepository::findIdByShareKey)
            ?: throw BusinessException(FundingErrorCode.FUNDING_NOT_FOUND)
}
