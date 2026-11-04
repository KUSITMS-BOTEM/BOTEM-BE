package com.botem.global.notification

import com.botem.global.property.DiscordProperties
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.core.task.TaskExecutor
import org.springframework.http.MediaType
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@Component
class DiscordErrorNotifier(
    private val discordProperties: DiscordProperties,
    restClientBuilder: RestClient.Builder,
    private val applicationTaskExecutor: TaskExecutor,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    private val restClient = restClientBuilder.build()

    // ponytail: 인스턴스 메모리 기준 중복 억제, 서버가 여러 대로 늘면 인스턴스마다 따로 알림이 감
    private val lastSentAt = ConcurrentHashMap<String, Instant>()

    fun notify(exception: Throwable, request: HttpServletRequest?) {
        val webhookUrl = discordProperties.errorWebhookUrl

        if (webhookUrl.isBlank()) {
            return
        }

        try {
            val appFrames = exception.stackTrace.filter { it.className.startsWith(APP_PACKAGE) }
            val dedupKey = "${exception.javaClass.name}@${appFrames.firstOrNull() ?: exception.stackTrace.firstOrNull()}"

            if (isDuplicate(dedupKey, Instant.now())) {
                return
            }

            val payload = buildPayload(
                exception = exception,
                // 쿼리스트링에 인가 코드 같은 민감 값이 담길 수 있어 경로만 사용
                requestLine = request?.let { "${it.method} ${it.requestURI}" } ?: "(요청 정보 없음)",
                userId = SecurityContextHolder.getContext().authentication?.principal as? Long,
                appFrames = appFrames.ifEmpty { exception.stackTrace.toList() },
            )

            applicationTaskExecutor.execute { send(webhookUrl, payload) }
        } catch (exception: Exception) {
            log.warn("디스코드 에러 알림 준비 실패", exception)
        }
    }

    private fun isDuplicate(key: String, now: Instant): Boolean {
        var duplicate = false

        lastSentAt.compute(key) { _, last ->
            if (last != null && now < last.plus(DEDUP_WINDOW)) {
                duplicate = true
                last
            } else {
                now
            }
        }

        return duplicate
    }

    private fun send(webhookUrl: String, payload: Map<String, Any>) {
        try {
            restClient.post()
                .uri(webhookUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .toBodilessEntity()
        } catch (exception: Exception) {
            // error로 남기면 로그 기반 알림 도입 시 재귀 알림이 될 수 있어 warn 사용
            log.warn("디스코드 에러 알림 전송 실패: {}", exception.message)
        }
    }

    companion object {
        private const val APP_PACKAGE = "com.botem"
        private const val MAX_MESSAGE_LENGTH = 500
        private const val MAX_FRAME_COUNT = 5
        private const val MAX_CAUSE_DEPTH = 10
        private const val EMBED_COLOR_RED = 0xE74C3C
        private val DEDUP_WINDOW = Duration.ofMinutes(1)
        private const val BOT_NAME = "BOTEM 알림봇 💰"
        private val CUTE_MESSAGES = listOf(
            "서버가 아야했어요. 호호 불어주세요 🩹",
            "범인은 이 안에 있어요 🕵️",
            "지금 확인하면 야근을 막을 수 있어요 🌙",
            "로그가 당신을 부르고 있어요 📜",
            "버그 한 마리 출몰! 잡으러 가요 🐛",
        )

        internal fun buildPayload(
            exception: Throwable,
            requestLine: String,
            userId: Long?,
            appFrames: List<StackTraceElement>,
        ): Map<String, Any> {
            val message = exception.message?.take(MAX_MESSAGE_LENGTH) ?: "(없음)"
            val frames = appFrames.take(MAX_FRAME_COUNT).joinToString("\n").ifEmpty { "(없음)" }
            val rootCause = generateSequence(exception) { it.cause }.take(MAX_CAUSE_DEPTH).last()
            val rootCauseText = if (rootCause === exception) {
                "(없음)"
            } else {
                "${rootCause.javaClass.name}: ${rootCause.message ?: "(메시지 없음)"}".take(MAX_MESSAGE_LENGTH)
            }

            return mapOf(
                "username" to BOT_NAME,
                "content" to "🚨 **[BE] 서버 에러가 발생했어요!** ${CUTE_MESSAGES.random()}",
                "embeds" to listOf(
                    mapOf(
                        "title" to "🔥 ${exception.javaClass.simpleName}",
                        "color" to EMBED_COLOR_RED,
                        "fields" to listOf(
                            field("📍 요청", "`$requestLine`", inline = true),
                            field("👤 userId", userId?.let { "`$it`" } ?: "비로그인", inline = true),
                            field("💥 예외", "`${exception.javaClass.name}`"),
                            field("💬 메시지", codeBlock(message)),
                            field("🧬 근본 원인", codeBlock(rootCauseText)),
                            field("🗺️ 위치", codeBlock(frames)),
                        ),
                        "footer" to mapOf("text" to "BOTEM Backend 💰 전체 스택트레이스는 서버 로그를 확인해주세요"),
                        "timestamp" to Instant.now().toString(),
                    ),
                ),
            )
        }

        private fun field(name: String, value: String, inline: Boolean = false) =
            mapOf("name" to name, "value" to value, "inline" to inline)

        private fun codeBlock(text: String) = "```\n${text.replace("```", "'''")}\n```"
    }
}
