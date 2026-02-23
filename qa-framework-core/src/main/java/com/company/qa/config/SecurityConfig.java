package com.company.qa.config;

import com.company.qa.security.ApiKeyAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;
    // Spring auto-injects the CorsConfigurationSource bean from CorsConfig
    private final CorsConfigurationSource corsConfigurationSource;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Enable CORS using our CorsConfig bean
                // This must be BEFORE csrf disable so OPTIONS pre-flight passes
                .cors(cors -> cors.configurationSource(corsConfigurationSource))

                // Disable CSRF for stateless API
                .csrf(AbstractHttpConfigurer::disable)

                // Disable form login
                .formLogin(AbstractHttpConfigurer::disable)

                // Disable HTTP Basic
                .httpBasic(AbstractHttpConfigurer::disable)

                // Stateless session
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // Configure authorization
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints - health, swagger, h2-console
                        .requestMatchers("/actuator/health", "/actuator/info", "/apple-touch-icon", "/favicon").permitAll()
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/h2-console").permitAll()

                        // API key creation is public (used by Login page "Create Key" tab)
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/api-keys").permitAll()

                        // OPTIONS pre-flight must be permitted globally (CORS requirement)
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // Static React frontend files served from Spring Boot classpath
                        // This allows the built React app to be served at / without auth
                        .requestMatchers("/", "/index.html", "/static/**", "/manifest.json",
                                "/robots.txt", "/favicon.ico", "/logo*.png").permitAll()

                        // All API endpoints require authentication
                        .anyRequest().authenticated()
                )

                // Add custom API key filter
                .addFilterBefore(apiKeyAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class)

                // Security headers
                .headers(headers -> headers
                        .contentTypeOptions(contentType -> {})
                        .xssProtection(xss -> {})
                        // Allow iframes for h2-console (dev only). Deny in prod via Nginx header.
                        .frameOptions(frame -> frame.sameOrigin())
                );

        return http.build();
    }
}