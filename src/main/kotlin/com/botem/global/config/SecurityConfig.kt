package com.botem.global.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain

@Configuration
class SecurityConfig {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http.authorizeHttpRequests {
            it.requestMatchers(*SWAGGER_URIS).permitAll()
            it.anyRequest().authenticated()
        }

        return http.build()
    }

    companion object {
        private val SWAGGER_URIS = arrayOf(
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs/**",
        )
    }
}
