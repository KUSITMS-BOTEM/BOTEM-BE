package com.botem.domain.funding.repository

import com.botem.domain.funding.entity.Funding
import com.botem.domain.funding.repository.projection.ProfileFundingTypeProjection
import java.time.LocalDateTime

interface FundingLifecycleQueryRepository {

    fun countActiveFundings(ownerId: Long, now: LocalDateTime): Long

    fun findProfileFundings(userId: Long, now: LocalDateTime, includeSurprise: Boolean): List<Funding>

    fun findProfileFundingTypes(userIds: Collection<Long>, now: LocalDateTime): List<ProfileFundingTypeProjection>
}
