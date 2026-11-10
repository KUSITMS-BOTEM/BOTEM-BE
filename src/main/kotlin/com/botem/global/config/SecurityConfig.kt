package com.botem.global.config

import com.botem.domain.user.repository.UserRepository
import com.botem.global.jwt.JwtAuthenticationEntryPoint
import com.botem.global.jwt.JwtAuthenticationFilter
import com.botem.global.jwt.JwtProvider
import com.botem.global.property.AdminProperties
import com.botem.global.security.AdminApiKeyFilter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
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
            allowedOrigins = listOf("https://botem.site", "https://botem-fe.vercel.app", "http://localhost:3000")
            allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            allowedHeaders = listOf("Authorization", "Content-Type", "sentry-trace", "baggage")
            allowCredentials = true
        }

        return UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/**", configuration)
        }
    }

    companion object {
        private val AUTH_URIS = arrayOf(
            "/api/auth/kakao/login-uri",
            "/api/auth/kakao/login",
            "/api/auth/reissue",
            "/api/auth/logout",
        )
    }
}
