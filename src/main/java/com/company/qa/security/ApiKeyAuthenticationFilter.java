package com.company.qa.security;

import com.company.qa.model.entity.ApiKey;
import com.company.qa.service.ApiKeyService;
import com.company.qa.service.RequestLogService;
import com.company.qa.util.SecurityUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
@RequiredArgsConstructor
@Slf4j
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-API-Key";
    private static final int RATE_LIMIT_REQUESTS = 100;
    private static final int RATE_LIMIT_WINDOW_MINUTES = 1;

    private final ApiKeyService apiKeyService;
    private final RequestLogService requestLogService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        long startTime = System.currentTimeMillis();
        String requestPath = request.getRequestURI();

        try {
            // Skip authentication for public endpoints
            if (isPublicEndpoint(requestPath)) {
                filterChain.doFilter(request, response);
                return;
            }

            // Extract API key from header
            String apiKeyValue = request.getHeader(API_KEY_HEADER);

            if (apiKeyValue == null || apiKeyValue.trim().isEmpty()) {
                log.warn("Missing API key for request: {}", requestPath);
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("{\"error\":\"API key required\"}");
                return;
            }

            // Validate API key
            ApiKey apiKey = apiKeyService.validateApiKey(apiKeyValue);

            if (apiKey == null) {
                log.warn("Invalid API key for request: {}", requestPath);
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("{\"error\":\"Invalid API key\"}");
                return;
            }

            // Check rate limit
            long requestCount = requestLogService.getRequestCount(
                    apiKey.getId(),
                    RATE_LIMIT_WINDOW_MINUTES
            );

            if (requestCount >= RATE_LIMIT_REQUESTS) {
                log.warn("Rate limit exceeded for API key: {}", apiKey.getName());
                response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
                response.getWriter().write("{\"error\":\"Rate limit exceeded\"}");
                return;
            }

            // Set authentication in security context
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            apiKey.getName(),
                            null,
                            Collections.singletonList(new SimpleGrantedAuthority("ROLE_API_USER"))
                    );
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);

            // Update last used timestamp (async)
            apiKeyService.updateLastUsed(apiKey.getId());

            // Continue filter chain
            filterChain.doFilter(request, response);

            // Log request (async)
            long responseTime = System.currentTimeMillis() - startTime;
            String ipAddress = SecurityUtils.getClientIpAddress(
                    request.getHeader("X-Forwarded-For"),
                    request.getRemoteAddr()
            );

            requestLogService.logRequest(
                    apiKey.getId(),
                    request.getMethod(),
                    requestPath,
                    ipAddress,
                    request.getHeader("User-Agent"),
                    response.getStatus(),
                    (int) responseTime
            );

        } catch (Exception e) {
            log.error("Error in API key filter: {}", e.getMessage(), e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"error\":\"Authentication error\"}");
        }
    }

    private boolean isPublicEndpoint(String path) {
        return path.startsWith("/actuator/health") ||
                path.startsWith("/actuator/info") ||
                path.startsWith("/swagger-ui") ||
                path.startsWith("/api/v1/auth/api-keys") ||
                path.startsWith("/v3/api-docs");
    }
}