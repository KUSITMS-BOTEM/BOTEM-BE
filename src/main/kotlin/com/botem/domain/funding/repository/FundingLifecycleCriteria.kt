package com.botem.domain.funding.repository

import com.botem.domain.funding.entity.Funding
import com.botem.domain.funding.entity.FundingStatus
import com.botem.domain.funding.entity.FundingType
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.Predicate
import jakarta.persistence.criteria.Root
import java.time.LocalDateTime

/**
 * 펀딩 목록 조회에서 공통으로 사용하는 시간 기반 라이프사이클 조건이다.
 *
 * deadline과 revealAt은 시간이 지나도 DB 상태를 자동으로 바꾸지 않으므로,
 * 조회 시점의 now와 함께 판단해야 한다.
 */
internal object FundingLifecycleCriteria {

    fun active(
        criteriaBuilder: CriteriaBuilder,
        funding: Root<Funding>,
        now: LocalDateTime,
    ): Predicate =
        criteriaBuilder.or(
            activeBirthday(criteriaBuilder, funding, now),
            activeSurprise(criteriaBuilder, funding, now),
        )

    fun activeBirthday(
        criteriaBuilder: CriteriaBuilder,
        funding: Root<Funding>,
        now: LocalDateTime,
    ): Predicate =
        criteriaBuilder.and(
            criteriaBuilder.equal(funding.get<FundingType>("type"), FundingType.BIRTHDAY),
            funding.get<FundingStatus>("status").`in`(ACTIVE_BIRTHDAY_STATUSES),
            criteriaBuilder.greaterThan(funding.get<LocalDateTime>("deadline"), now),
        )

    fun activeSurprise(
        criteriaBuilder: CriteriaBuilder,
        funding: Root<Funding>,
        now: LocalDateTime,
    ): Predicate =
        criteriaBuilder.and(
            criteriaBuilder.equal(funding.get<FundingType>("type"), FundingType.SURPRISE),
            funding.get<FundingStatus>("status").`in`(ACTIVE_SURPRISE_STATUSES),
            criteriaBuilder.greaterThan(funding.get<LocalDateTime>("revealAt"), now),
        )

    fun closedBirthday(
        criteriaBuilder: CriteriaBuilder,
        funding: Root<Funding>,
        now: LocalDateTime,
    ): Predicate =
        criteriaBuilder.and(
            criteriaBuilder.equal(funding.get<FundingType>("type"), FundingType.BIRTHDAY),
            criteriaBuilder.or(
                criteriaBuilder.lessThanOrEqualTo(funding.get<LocalDateTime>("deadline"), now),
                criteriaBuilder.isNotNull(funding.get<LocalDateTime>("earlyClosedAt")),
                criteriaBuilder.isNotNull(funding.get<LocalDateTime>("resultOpenedAt")),
            ),
        )

    fun closedSurpriseForOwner(
        criteriaBuilder: CriteriaBuilder,
        funding: Root<Funding>,
        now: LocalDateTime,
    ): Predicate =
        criteriaBuilder.and(
            criteriaBuilder.equal(funding.get<FundingType>("type"), FundingType.SURPRISE),
            criteriaBuilder.lessThanOrEqualTo(funding.get<LocalDateTime>("revealAt"), now),
            funding.get<FundingStatus>("status").`in`(CLOSED_SURPRISE_OWNER_STATUSES),
        )

    fun archived(
        criteriaBuilder: CriteriaBuilder,
        funding: Root<Funding>,
        now: LocalDateTime,
    ): Predicate =
        criteriaBuilder.and(
            criteriaBuilder.notEqual(funding.get<FundingStatus>("status"), FundingStatus.CANCELLED),
            criteriaBuilder.or(
                closedBirthday(criteriaBuilder, funding, now),
                criteriaBuilder.and(
                    criteriaBuilder.equal(funding.get<FundingType>("type"), FundingType.SURPRISE),
                    criteriaBuilder.lessThanOrEqualTo(funding.get<LocalDateTime>("revealAt"), now),
                ),
            ),
        )

    val activeBirthdayStatuses: Set<FundingStatus>
        get() = ACTIVE_BIRTHDAY_STATUSES

    val activeSurpriseStatuses: Set<FundingStatus>
        get() = ACTIVE_SURPRISE_STATUSES

    private val ACTIVE_BIRTHDAY_STATUSES = setOf(
        FundingStatus.FUNDING,
        FundingStatus.CLOSED,
    )
    private val ACTIVE_SURPRISE_STATUSES = setOf(
        FundingStatus.FUNDING,
        FundingStatus.CLOSED,
        FundingStatus.GIFT_PREPARING,
        FundingStatus.WAITING_REVEAL,
    )
    private val CLOSED_SURPRISE_OWNER_STATUSES = setOf(
        FundingStatus.WAITING_REVEAL,
        FundingStatus.REVEALED,
        FundingStatus.WAITING_GIFT_VERIFICATION,
        FundingStatus.COMPLETED,
    )
}
