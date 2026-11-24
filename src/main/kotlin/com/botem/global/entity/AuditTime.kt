package com.botem.global.entity

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

private val KOREA_ZONE: ZoneId = ZoneId.of("Asia/Seoul")

// 감사 시각(createdAt·updatedAt)은 서버 시간대와 관계없이 UTC로 저장한다(JpaAuditingConfig). 화면에 보여줄 때 한국 시간으로 바꾼다
fun LocalDateTime.auditTimeToKorea(): LocalDateTime =
    atZone(ZoneOffset.UTC).withZoneSameInstant(KOREA_ZONE).toLocalDateTime()

// 업무 시각(Clock, 한국 시간)을 감사 시각과 비교하거나 감사 컬럼에 직접 쓸 때 UTC로 바꾼다
fun LocalDateTime.koreaTimeToAudit(): LocalDateTime =
    atZone(KOREA_ZONE).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime()
