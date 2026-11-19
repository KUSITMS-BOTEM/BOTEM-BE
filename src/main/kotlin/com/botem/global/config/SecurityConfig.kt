package com.botem.global.config

import com.botem.domain.user.repository.UserRepository
import com.botem.global.jwt.JwtAuthenticationEntryPoint
import com.botem.global.jwt.JwtAuthenticationFilter
import com.botem.global.jwt.JwtProvider
import com.botem.global.property.AdminProperties
import com.botem.global.security.AdminApiKeyFilter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.authorization.AuthorizationDecision
import org.springframework.security.authorization.AuthorizationManager
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.access.intercept.RequestAuthorizationContext
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
class SecurityConfig(
    private val jwtProvider: JwtProvider,
    private val jwtAuthenticationEntryPoint: JwtAuthenticationEntryPoint,
    private val userRepository: UserRepository,
    private val adminProperties: AdminProperties,
) {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .cors { }
            .csrf { it.disable() }
            .formLogin { it.disable() }
            .httpBasic { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it.requestMatchers(*AUTH_URIS).permitAll()
                it.requestMatchers("/actuator/health").permitAll()
                it.requestMatchers(HttpMethod.GET, "/api/fundings/{fundingId:\\d+}").permitAll()
                it.requestMatchers(HttpMethod.GET, "/api/thank-cards/*").permitAll()
                it.requestMatchers(HttpMethod.GET, "/api/wish-profiles/*").permitAll()
                it.requestMatchers(HttpMethod.POST, *GUEST_ALLOWED_POST_URIS).access(REJECT_INVALID_TOKEN)
                it.requestMatchers(HttpMethod.PATCH, GUEST_CONTRIBUTION_URI).access(REJECT_INVALID_TOKEN)
                it.requestMatchers(HttpMethod.DELETE, GUEST_CONTRIBUTION_URI).access(REJECT_INVALID_TOKEN)
                it.requestMatchers(AdminApiKeyFilter.ADMIN_URI_PATTERN).hasRole("ADMIN")
                it.anyRequest().authenticated()
            }
            .exceptionHandling { it.authenticationEntryPoint(jwtAuthenticationEntryPoint) }
            .addFilterBefore(
                JwtAuthenticationFilter(jwtProvider, userRepository),
                UsernamePasswordAuthenticationFilter::class.java,
            )
            .addFilterAfter(AdminApiKeyFilter(adminProperties.apiKey), JwtAuthenticationFilter::class.java)

        return http.build()
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration().apply {
            allowedOrigins = listOf("https://botem.me", "https://botem.site", "https://botem-fe.vercel.app", "http://localhost:3000")
            allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            allowedHeaders = listOf(
                "Authorization",
                "Content-Type",
                "X-Guest-Contribution-Token",
                "sentry-trace",
                "baggage",
            )
            allowCredentials = true
        }

        return UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/**", configuration)
        }
    }

    companion object {
        private val GUEST_ALLOWED_POST_URIS = arrayOf(
            "/api/fundings/{fundingId:\\d+}/contributions",
            "/api/fundings/{fundingId:\\d+}/contributions/message-only",
            "/api/fundings/{fundingId:\\d+}/contributions/{contributionId:\\d+}/transfer-sent",
            "/api/fundings/{fundingId:\\d+}/contributions/guest/access",
            "/api/fundings/{fundingId:\\d+}/rolling-paper-attachments/upload-url",
        )

        private const val GUEST_CONTRIBUTION_URI =
            "/api/fundings/{fundingId:\\d+}/contributions/guest"

        // 비로그인도 허용하지만, 보낸 토큰이 유효하지 않으면 본문 검증보다 먼저 401로 응답한다
        private val REJECT_INVALID_TOKEN = AuthorizationManager<RequestAuthorizationContext> { _, context ->
            AuthorizationDecision(context.request.getAttribute(JwtAuthenticationFilter.ERROR_CODE_ATTRIBUTE) == null)
        }

        private val AUTH_URIS = arrayOf(
            "/api/auth/kakao/login-uri",
            "/api/auth/kakao/login",
            "/api/auth/reissue",
            "/api/auth/logout",
        )
    }
}
