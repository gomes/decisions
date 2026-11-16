package io.github.gomes.decisions.web.api;

import io.github.gomes.decisions.config.DecisionsProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Optional shared-secret protection for {@code /api/**}: active only when {@code decisions.api-token}
 * (env {@code DECISIONS_GATEWAY_TOKEN}) is set. The playground and actuator health are not affected.
 */
@Component
public class ApiTokenFilter extends OncePerRequestFilter {

    private final byte[] token;

    public ApiTokenFilter(DecisionsProperties properties) {
        this.token = properties.apiTokenRequired() ? properties.apiToken().getBytes(StandardCharsets.UTF_8) : null;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return token == null || !request.getRequestURI().startsWith(request.getContextPath() + "/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")
                && MessageDigest.isEqual(header.substring(7).getBytes(StandardCharsets.UTF_8), token)) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("""
                {"type":"about:blank","title":"Unauthorized","status":401,\
                "detail":"missing or invalid bearer token"}""");
    }
}
