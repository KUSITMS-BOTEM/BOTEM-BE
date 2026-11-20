package com.botem.global.migration

import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import org.springframework.test.context.TestPropertySource
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.mysql.MySQLContainer
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// 다른 테스트는 H2에 엔티티로 스키마를 만들어 Flyway를 실행하지 않는다.
// 운영과 같은 MySQL에 모든 마이그레이션을 적용하고, 엔티티 매핑이 그 스키마와 맞는지(ddl-auto=validate) 확인한다.
// 컨텍스트가 뜨지 않으면(마이그레이션 실패·validate 실패) 이 테스트가 실패한다
@DataJpaTest
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

    @Test
    fun `운영과 같은 MySQL에 모든 마이그레이션을 적용하고 엔티티 매핑이 스키마와 맞는다`() {
        val info = flyway.info()

        assertTrue(info.pending().isEmpty(), "적용되지 않은 마이그레이션: ${info.pending().map { it.script }}")
        assertEquals(latestMigrationVersion(), info.current()?.version?.version)
    }

    private fun latestMigrationVersion(): String =
        PathMatchingResourcePatternResolver()
            .getResources("classpath:db/migration/V*__*.sql")
            .mapNotNull { MIGRATION_VERSION.find(it.filename.orEmpty())?.groupValues?.get(1) }
            .maxBy(String::toInt)

    companion object {
        private val MIGRATION_VERSION = Regex("^V(\\d+)__")

        @Container
        @ServiceConnection
        @JvmStatic
        val mysql: MySQLContainer = MySQLContainer("mysql:8.4")
    }
}
