package com.botem.domain.funding.repository

import com.botem.domain.funding.entity.Funding
import com.botem.domain.funding.entity.FundingType
import com.botem.domain.funding.entity.Contribution
import com.botem.domain.funding.entity.ContributionStatus
import com.botem.domain.funding.repository.projection.ProfileFundingTypeProjection
import jakarta.persistence.EntityManager
import java.time.LocalDateTime

class FundingLifecycleQueryRepositoryImpl(
    private val entityManager: EntityManager,
) : FundingLifecycleQueryRepository {

    override fun countActiveFundings(ownerId: Long, now: LocalDateTime): Long {
        val criteriaBuilder = entityManager.criteriaBuilder
        val query = criteriaBuilder.createQuery(Long::class.java)
        val funding = query.from(Funding::class.java)

        query.select(criteriaBuilder.count(funding))
            .where(
                criteriaBuilder.and(
                    criteriaBuilder.equal(funding.get<Long>("ownerId"), ownerId),
                    FundingLifecycleCriteria.active(criteriaBuilder, funding, now),
                ),
            )

        return entityManager.createQuery(query).singleResult
    }

    override fun findProfileFundings(userId: Long, now: LocalDateTime, includeSurprise: Boolean): List<Funding> {
        val criteriaBuilder = entityManager.criteriaBuilder
        val query = criteriaBuilder.createQuery(Funding::class.java)
        val funding = query.from(Funding::class.java)
        val birthday = criteriaBuilder.and(
            FundingLifecycleCriteria.activeBirthday(criteriaBuilder, funding, now),
            criteriaBuilder.equal(funding.get<Long>("ownerId"), userId),
        )
        val predicate = if (includeSurprise) {
            criteriaBuilder.or(
                birthday,
                criteriaBuilder.and(
                    FundingLifecycleCriteria.activeSurprise(criteriaBuilder, funding, now),
                    criteriaBuilder.equal(funding.get<Long>("recipientId"), userId),
                    criteriaBuilder.isTrue(funding.get<Boolean>("showOnRecipientProfile")),
                ),
            )
        } else {
            birthday
        }

        query.select(funding)
            .where(predicate)
            .orderBy(criteriaBuilder.asc(funding.get<LocalDateTime>("deadline")), criteriaBuilder.asc(funding.get<Long>("id")))

        return entityManager.createQuery(query).resultList
    }

    override fun findProfileFundingTypes(userIds: Collection<Long>, now: LocalDateTime): List<ProfileFundingTypeProjection> {
        if (userIds.isEmpty()) return emptyList()

        val criteriaBuilder = entityManager.criteriaBuilder
        val query = criteriaBuilder.createQuery(ProfileFundingTypeProjection::class.java)
        val funding = query.from(Funding::class.java)
        val profileUserId = criteriaBuilder.selectCase<Long>()
            .`when`(
                criteriaBuilder.equal(funding.get<FundingType>("type"), FundingType.BIRTHDAY),
                funding.get<Long>("ownerId"),
            )
            .otherwise(funding.get<Long>("recipientId"))

        query.select(
            criteriaBuilder.construct(
                ProfileFundingTypeProjection::class.java,
                profileUserId,
                funding.get<FundingType>("type"),
            ),
        ).distinct(true)
            .where(
                criteriaBuilder.or(
                    criteriaBuilder.and(
                        FundingLifecycleCriteria.activeBirthday(criteriaBuilder, funding, now),
                        funding.get<Long>("ownerId").`in`(userIds),
                    ),
                    criteriaBuilder.and(
                        FundingLifecycleCriteria.activeSurprise(criteriaBuilder, funding, now),
                        funding.get<Long>("recipientId").`in`(userIds),
                        criteriaBuilder.isTrue(funding.get<Boolean>("showOnRecipientProfile")),
                    ),
                ),
            )

        return entityManager.createQuery(query).resultList
    }

    override fun findClosedFundingsByOwnerId(ownerId: Long, now: LocalDateTime): List<Funding> {
        val criteriaBuilder = entityManager.criteriaBuilder
        val query = criteriaBuilder.createQuery(Funding::class.java)
        val funding = query.from(Funding::class.java)

        query.select(funding)
            .where(
                criteriaBuilder.and(
                    criteriaBuilder.equal(funding.get<Long>("ownerId"), ownerId),
                    criteriaBuilder.notEqual(
                        funding.get<com.botem.domain.funding.entity.FundingStatus>("status"),
                        com.botem.domain.funding.entity.FundingStatus.CANCELLED,
                    ),
                    criteriaBuilder.or(
                        FundingLifecycleCriteria.closedBirthday(criteriaBuilder, funding, now),
                        FundingLifecycleCriteria.closedSurpriseForOwner(criteriaBuilder, funding, now),
                    ),
                ),
            )
            .orderBy(criteriaBuilder.desc(funding.get<LocalDateTime>("deadline")), criteriaBuilder.desc(funding.get<Long>("id")))

        return entityManager.createQuery(query).resultList
    }

    override fun findArchivedFundingsByUserId(userId: Long, now: LocalDateTime): List<Funding> {
        val criteriaBuilder = entityManager.criteriaBuilder
        val query = criteriaBuilder.createQuery(Funding::class.java)
        val funding = query.from(Funding::class.java)
        val participated = query.subquery(Long::class.java)
        val contribution = participated.from(Contribution::class.java)
        participated.select(contribution.get<Long>("id"))
            .where(
                criteriaBuilder.equal(contribution.get<Long>("fundingId"), funding.get<Long>("id")),
                criteriaBuilder.equal(contribution.get<Long>("participantUserId"), userId),
                criteriaBuilder.notEqual(contribution.get<ContributionStatus>("status"), ContributionStatus.CANCELLED),
            )

        query.select(funding)
            .where(
                criteriaBuilder.and(
                    FundingLifecycleCriteria.archived(criteriaBuilder, funding, now),
                    criteriaBuilder.or(
                        criteriaBuilder.equal(funding.get<Long>("ownerId"), userId),
                        criteriaBuilder.equal(funding.get<Long>("recipientId"), userId),
                        criteriaBuilder.exists(participated),
                    ),
                ),
            )
            .orderBy(criteriaBuilder.desc(funding.get<LocalDateTime>("deadline")), criteriaBuilder.desc(funding.get<Long>("id")))

        return entityManager.createQuery(query).resultList
    }
}
