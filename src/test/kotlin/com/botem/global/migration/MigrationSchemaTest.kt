package com.botem.global.migration

import com.botem.domain.account.entity.Bank
import com.botem.domain.funding.entity.Contribution
import com.botem.domain.funding.entity.ContributionStatus
import com.botem.domain.funding.entity.Funding
import com.botem.domain.funding.entity.FundingPayoutAccountSnapshot
import com.botem.domain.funding.entity.FundingProductSnapshot
import com.botem.domain.funding.repository.ContributionRepository
import com.botem.domain.funding.repository.FundingRepository
import com.botem.domain.user.entity.User
import com.botem.domain.user.repository.UserRepository
import com.botem.global.config.JpaAuditingConfig
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Import
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import org.springframework.test.context.TestPropertySource
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.mysql.MySQLContainer
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// 다른 테스트는 H2에 엔티티로 스키마를 만들어 Flyway를 실행하지 않는다.
// 운영과 같은 MySQL에 모든 마이그레이션을 적용하고, 엔티티 매핑이 그 스키마와 맞는지(ddl-auto=validate) 확인한다.
// 컨텍스트가 뜨지 않으면(마이그레이션 실패·validate 실패) 이 테스트가 실패한다.
// Docker가 필요해 일반 test에서는 빠지고 `./gradlew migrationTest`로 실행한다
@Tag("migration")
@DataJpaTest
@Import(JpaAuditingConfig::class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(
    properties = [
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.datasource.driver-class-name=",
    ],
)
class MigrationSchemaTest {

    @Autowired
    private lateinit var flyway: Flyway

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var fundingRepository: FundingRepository

    @Autowired
    private lateinit var contributionRepository: ContributionRepository

    @Test
    fun `운영과 같은 MySQL에 모든 마이그레이션을 적용하고 엔티티 매핑이 스키마와 맞는다`() {
        val info = flyway.info()

        assertTrue(info.pending().isEmpty(), "적용되지 않은 마이그레이션: ${info.pending().map { it.script }}")
        assertEquals(latestMigrationVersion(), info.current()?.version?.version)
    }

    // 네이티브 쿼리는 다른 테스트에서 H2로만 실행되므로, MySQL 문법(윈도 함수, FOR UPDATE 등)이 실제로 도는지 확인한다
    @Test
    fun `MySQL 전용 네이티브 쿼리가 실제 MySQL에서 동작한다`() {
        val owner = userRepository.saveAndFlush(User(kakaoId = 1L, nickname = "지유", profileImageUrl = null))
        val friend = userRepository.saveAndFlush(
            User(kakaoId = 2L, nickname = "서준", profileImageUrl = PROFILE_IMAGE_URL, wishProfileKey = null),
        )
        val funding = fundingRepository.saveAndFlush(birthdayFunding(owner.id))
        listOf(
            contribution(funding.id, friend.id, "서준"),
            contribution(funding.id, friend.id, "서준"),
            contribution(funding.id, null, "민지"),
        ).forEach(contributionRepository::saveAndFlush)

        // 같은 회원의 여러 보탬은 한 명으로 묶고, 첫 참여 순서대로 미리보기를 만든다
        val previews = contributionRepository.findParticipantPreviewsByFundingIds(listOf(funding.id))
        assertEquals(listOf("서준", "민지"), previews.map { it.participantName })
        assertEquals(PROFILE_IMAGE_URL, previews.first().profileImageUrl)

        assertEquals(1, userRepository.updateWishProfileKeyIfNull(friend.id, WISH_PROFILE_KEY))
        assertEquals(0, userRepository.updateWishProfileKeyIfNull(friend.id, "ffffffffffffffffffffffffffffffff"))
        assertEquals(WISH_PROFILE_KEY, userRepository.findWishProfileKeyForUpdate(friend.id))
    }

    private fun birthdayFunding(ownerId: Long): Funding =
        Funding.createBirthday(
            ownerId = ownerId,
            targetAmount = 100_000L,
            deadline = NOW.plusDays(3),
            story = "새 헤드폰이 필요해요",
            promise = null,
            product = FundingProductSnapshot(null, "무선 헤드폰", "https://image.example.com/p.jpg", 100_000L, "https://shop.example.com/p"),
            payoutAccount = FundingPayoutAccountSnapshot(null, Bank.KAKAO_BANK, "encrypted-account-number", "지유"),
            now = NOW,
        )

    private fun contribution(fundingId: Long, userId: Long?, name: String): Contribution =
        Contribution(
            fundingId = fundingId,
            participantUserId = userId,
            participantName = name,
            amount = 10_000L,
            status = ContributionStatus.AWAITING_TRANSFER,
            guestPasswordHash = if (userId == null) "guest-password-hash" else null,
        )

    private fun latestMigrationVersion(): String =
        PathMatchingResourcePatternResolver()
            .getResources("classpath:db/migration/V*__*.sql")
            .mapNotNull { MIGRATION_VERSION.find(it.filename.orEmpty())?.groupValues?.get(1) }
            .maxBy(String::toInt)

    companion object {
        private val MIGRATION_VERSION = Regex("^V(\\d+)__")
        private val NOW = LocalDateTime.of(2026, 11, 21, 12, 0)
        private const val PROFILE_IMAGE_URL = "https://image.example.com/seojun.jpg"
        private const val WISH_PROFILE_KEY = "0123456789abcdef0123456789abcdef"

        @Container
        @ServiceConnection
        @JvmStatic
        val mysql: MySQLContainer = MySQLContainer("mysql:8.4")
    }
}
