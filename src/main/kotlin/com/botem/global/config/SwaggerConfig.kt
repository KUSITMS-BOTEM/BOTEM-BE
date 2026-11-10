package com.botem.global.config

import com.botem.global.property.SwaggerProperties
import com.botem.global.security.AdminApiKeyFilter
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.userdetails.User
import org.springframework.security.crypto.factory.PasswordEncoderFactories
import org.springframework.security.provisioning.InMemoryUserDetailsManager
import org.springframework.security.web.SecurityFilterChain

@Configuration
class SwaggerConfig(
    private val swaggerProperties: SwaggerProperties,
) {

    @Bean
    fun openAPI(): OpenAPI = OpenAPI()
        .info(
            Info()
                .title("BOTEM API")
                .version("v1"),
        )
        .components(
            Components()
                .addSecuritySchemes(
                    BEARER_AUTH,
                    SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT"),
                )
                .addSecuritySchemes(
                    ADMIN_API_KEY_AUTH,
                    SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .`in`(SecurityScheme.In.HEADER)
                        .name(AdminApiKeyFilter.ADMIN_API_KEY_HEADER),
                ),
        )
        .addSecurityItem(SecurityRequirement().addList(BEARER_AUTH))

    // JWT 필터 체인보다 먼저 Swagger 경로만 처리
    @Bean
    @Order(1)
    fun swaggerSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .securityMatcher(*SWAGGER_URIS)
            .csrf { it.disable() }
            .formLogin { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }

        if (swaggerProperties.username.isBlank()) {
            http.authorizeHttpRequests { it.anyRequest().permitAll() }

            return http.build()
        }

        val passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder()
        val swaggerUser = User.withUsername(swaggerProperties.username)
            .password(passwordEncoder.encode(swaggerProperties.password))
            .build()

        http
            .authorizeHttpRequests { it.anyRequest().authenticated() }
            .httpBasic { }
            .userDetailsService(InMemoryUserDetailsManager(swaggerUser))

        return http.build()
    }

    companion object {
        private const val BEARER_AUTH = "bearerAuth"
        const val ADMIN_API_KEY_AUTH = "adminApiKey"

        private val SWAGGER_URIS = arrayOf(
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs/**",
        )
    }
}
