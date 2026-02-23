package com.company.qa.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * CORS configuration for the QA Framework API.
 *
 * Strategy:
 * - Development (local): allows localhost:3000 (React dev server via proxy is fine,
 *   but direct calls from browser also work)
 * - Production (EC2 + Nginx): React is served by Nginx on same origin, so technically
 *   CORS is not needed for production — but we keep it permissive for API access from
 *   tools like Swagger UI, Postman, or direct curl during demo.
 *
 * Configured origins are driven by application properties so NO code change is needed
 * when switching EC2 instances — just update application-prod.yml or env vars.
 *
 * Wire-up: SecurityConfig picks up corsConfigurationSource() bean automatically
 * via http.cors(cors -> cors.configurationSource(corsConfigurationSource()))
 */
@Configuration
public class CorsConfig {

    // Comma-separated list of allowed origins.
    // Default covers local dev. Override in prod with CORS_ALLOWED_ORIGINS env var.
    // Example: CORS_ALLOWED_ORIGINS=https://1.2.3.4,https://yourdomain.com
    @Value("${cors.allowed-origins:http://localhost:3000,http://localhost:8080}")
    private List<String> allowedOrigins;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // Origins: from config. In production this will include the EC2 IP/domain
        config.setAllowedOrigins(allowedOrigins);

        // Standard HTTP methods needed by the React frontend
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));

        // Headers the frontend sends on every request
        config.setAllowedHeaders(List.of(
                "Content-Type",
                "X-API-Key",          // Our custom auth header
                "X-User-ID",          // Agent controller optional header
                "X-User-Name",        // Agent controller optional header
                "X-Requested-With",
                "Authorization",
                "Accept",
                "Origin"
        ));

        // Headers the browser is allowed to read from our responses
        config.setExposedHeaders(List.of(
                "X-API-Key",
                "Content-Disposition"  // Needed if we ever serve file downloads
        ));

        // Allow credentials (needed if browser caches headers between requests)
        config.setAllowCredentials(false);  // We use API key header, not cookies

        // Cache pre-flight OPTIONS response for 1 hour — reduces latency during demo
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // Apply CORS config to all API endpoints
        source.registerCorsConfiguration("/api/**", config);
        // Also apply to actuator endpoints (health check from Nginx)
        source.registerCorsConfiguration("/actuator/**", config);

        return source;
    }
}