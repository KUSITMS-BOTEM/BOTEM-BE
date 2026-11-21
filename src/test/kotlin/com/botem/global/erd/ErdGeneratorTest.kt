package com.botem.global.erd

import com.botem.global.erd.ErdGenerator.Column
import com.botem.global.erd.ErdGenerator.ForeignKey
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ErdGeneratorTest {

    @Test
    fun `키와 null 가능 여부를 표시하고 외래 키로 관계선을 그린다`() {
        val erd = ErdGenerator.render(
            columns = listOf(
                Column("users", "id", "bigint", nullable = false, primaryKey = true, unique = false),
                Column("fundings", "id", "bigint", nullable = false, primaryKey = true, unique = false),
                Column("fundings", "owner_id", "bigint", nullable = false, primaryKey = false, unique = false),
                Column("fundings", "recipient_id", "bigint", nullable = true, primaryKey = false, unique = false),
                Column("fundings", "share_key", "varchar", nullable = false, primaryKey = false, unique = true),
                Column("gift_verifications", "funding_id", "bigint", nullable = false, primaryKey = false, unique = true),
            ),
            foreignKeys = listOf(
                ForeignKey("fundings", "owner_id", "users"),
                ForeignKey("fundings", "recipient_id", "users"),
                ForeignKey("gift_verifications", "funding_id", "fundings"),
            ),
        )

        assertContains(erd, "        bigint id PK")
        assertContains(erd, "        bigint recipient_id FK \"nullable\"")
        assertContains(erd, "        varchar share_key UK")
        // 필수 외래 키는 1:N, null 가능 외래 키는 0..1:N, 외래 키 단독 UNIQUE는 1:1
        assertContains(erd, "    users ||--o{ fundings : \"owner_id\"")
        assertContains(erd, "    users |o--o{ fundings : \"recipient_id\"")
        assertContains(erd, "    fundings ||--o| gift_verifications : \"funding_id\"")
    }

    @Test
    fun `README의 ERD 표시 사이만 바꾸고 표시가 없으면 실패한다`() {
        val readme = "# 제목\n<!-- ERD:START -->\n옛 ERD\n<!-- ERD:END -->\n# 다음\n"

        assertEquals(
            "# 제목\n<!-- ERD:START -->\n새 ERD\n<!-- ERD:END -->\n# 다음\n",
            ErdGenerator.replaceSection(readme, "새 ERD"),
        )
        assertFailsWith<IllegalArgumentException> { ErdGenerator.replaceSection("# 표시 없음", "새 ERD") }
    }
}
