package com.botem.global.erd

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.TestPropertySource
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.mysql.MySQLContainer
import java.io.File

// `./gradlew generateErd`로 실행한다. 운영과 같은 MySQL에 마이그레이션을 적용한 실제 스키마로 README의 ERD를 다시 쓴다
@Tag("erd")
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
class ErdGenerationTest {

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `마이그레이션을 적용한 스키마로 README의 ERD를 갱신한다`() {
        val erd = ErdGenerator.render(columns(), foreignKeys())
        val readme = File(README_PATH)

        readme.writeText(ErdGenerator.replaceSection(readme.readText(), erd))
    }

    private fun columns(): List<ErdGenerator.Column> {
        val uniqueColumns = jdbcTemplate.queryForList(
            """
            SELECT TABLE_NAME, MAX(COLUMN_NAME) AS COLUMN_NAME
            FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE() AND NON_UNIQUE = 0 AND INDEX_NAME <> 'PRIMARY'
            GROUP BY TABLE_NAME, INDEX_NAME
            HAVING COUNT(*) = 1
            """.trimIndent(),
        ).map { it["TABLE_NAME"] to it["COLUMN_NAME"] }.toSet()

        return jdbcTemplate.query(
            """
            SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE, IS_NULLABLE, COLUMN_KEY
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME <> 'flyway_schema_history'
            ORDER BY TABLE_NAME, ORDINAL_POSITION
            """.trimIndent(),
        ) { row, _ ->
            val table = row.getString("TABLE_NAME")
            val column = row.getString("COLUMN_NAME")
            ErdGenerator.Column(
                table = table,
                name = column,
                type = row.getString("DATA_TYPE"),
                nullable = row.getString("IS_NULLABLE") == "YES",
                primaryKey = row.getString("COLUMN_KEY") == "PRI",
                unique = (table to column) in uniqueColumns,
            )
        }
    }

    private fun foreignKeys(): List<ErdGenerator.ForeignKey> =
        jdbcTemplate.query(
            """
            SELECT TABLE_NAME, COLUMN_NAME, REFERENCED_TABLE_NAME
            FROM information_schema.KEY_COLUMN_USAGE
            WHERE TABLE_SCHEMA = DATABASE() AND REFERENCED_TABLE_NAME IS NOT NULL
            """.trimIndent(),
        ) { row, _ ->
            ErdGenerator.ForeignKey(
                table = row.getString("TABLE_NAME"),
                column = row.getString("COLUMN_NAME"),
                referencedTable = row.getString("REFERENCED_TABLE_NAME"),
            )
        }

    companion object {
        // Gradle 테스트는 프로젝트 루트에서 실행된다
        private const val README_PATH = "README.md"

        @Container
        @ServiceConnection
        @JvmStatic
        val mysql: MySQLContainer = MySQLContainer("mysql:8.4")
    }
}
