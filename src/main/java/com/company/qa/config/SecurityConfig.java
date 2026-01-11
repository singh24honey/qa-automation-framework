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

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
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
                        // Public endpoints
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/api-keys").permitAll()

                        // All other endpoints require authentication
                        .anyRequest().authenticated()
                )

                // Add custom filter
                .addFilterBefore(apiKeyAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class)

                // Security headers
                .headers(headers -> headers
                        .contentTypeOptions(contentType -> {})
                        .xssProtection(xss -> {})
                        .frameOptions(frame -> frame.deny())
                );

        return http.build();
    }
}