package com.botem.global.erd

// information_schema에서 읽은 스키마를 mermaid erDiagram으로 그린다.
// 엔티티는 연관관계 없이 ID로 참조하므로 관계선은 DB 외래 키를 기준으로 한다
object ErdGenerator {

    data class Column(
        val table: String,
        val name: String,
        val type: String,
        val nullable: Boolean,
        val primaryKey: Boolean,
        val unique: Boolean,
    )

    data class ForeignKey(
        val table: String,
        val column: String,
        val referencedTable: String,
    )

    fun render(columns: List<Column>, foreignKeys: List<ForeignKey>): String {
        val foreignKeyColumns = foreignKeys.map { it.table to it.column }.toSet()
        val columnsByTable = columns.groupBy(Column::table).toSortedMap()

        val entities = columnsByTable.map { (table, tableColumns) ->
            val attributes = tableColumns.joinToString("\n") { column ->
                val keys = listOfNotNull(
                    "PK".takeIf { column.primaryKey },
                    "FK".takeIf { (table to column.name) in foreignKeyColumns },
                    "UK".takeIf { column.unique && !column.primaryKey },
                ).joinToString(", ")
                val comment = if (column.nullable) " \"nullable\"" else ""
                "        ${column.type} ${column.name}${if (keys.isEmpty()) "" else " $keys"}$comment"
            }
            "    $table {\n$attributes\n    }"
        }
        val relationships = foreignKeys
            .sortedWith(compareBy(ForeignKey::referencedTable, ForeignKey::table, ForeignKey::column))
            .map { foreignKey ->
                val column = columnsByTable.getValue(foreignKey.table).first { it.name == foreignKey.column }
                // 외래 키가 null 가능하면 부모가 없을 수 있고, 외래 키 단독 UNIQUE면 1:1이다
                val parent = if (column.nullable) "|o" else "||"
                val child = if (column.unique) "o|" else "o{"
                "    ${foreignKey.referencedTable} $parent--$child ${foreignKey.table} : \"${foreignKey.column}\""
            }

        return (listOf("```mermaid", "erDiagram") + entities + relationships + "```").joinToString("\n")
    }

    // README의 표시 사이만 바꿔 다른 내용은 건드리지 않는다
    fun replaceSection(readme: String, erd: String): String {
        val start = readme.indexOf(START_MARKER)
        val end = readme.indexOf(END_MARKER)
        require(start >= 0 && end > start) { "README에 $START_MARKER ~ $END_MARKER 표시가 필요합니다" }

        return readme.substring(0, start + START_MARKER.length) + "\n" + erd + "\n" + readme.substring(end)
    }

    const val START_MARKER = "<!-- ERD:START -->"
    const val END_MARKER = "<!-- ERD:END -->"
}
