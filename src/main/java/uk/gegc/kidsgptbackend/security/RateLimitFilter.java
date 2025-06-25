package uk.gegc.kidsgptbackend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

@Slf4j
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final Optional<RateLimitService> rateLimitService;

    @Value("${app.rate-limit.enabled:true}")
    private boolean rateLimitEnabled;

    @Autowired
    public RateLimitFilter(Optional<RateLimitService> rateLimitService) {
        this.rateLimitService = rateLimitService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, 
                                  FilterChain filterChain) throws ServletException, IOException {
        
        String requestURI = request.getRequestURI();
        log.debug("RateLimitFilter processing request: {} - Enabled: {}, Service available: {}", 
                requestURI, rateLimitEnabled, rateLimitService.isPresent());
        
        // Skip rate limiting if disabled or service not available
        if (!rateLimitEnabled || rateLimitService.isEmpty()) {
            log.debug("Skipping rate limiting for {}: enabled={}, service present={}", 
                    requestURI, rateLimitEnabled, rateLimitService.isPresent());
            filterChain.doFilter(request, response);
            return;
        }
        
        String method = request.getMethod();
        String authHeader = request.getHeader("Authorization");
        
        log.debug("Applying rate limiting to {} {}", method, requestURI);
        
        RateLimitService.RateLimitResult result = rateLimitService.get().checkRateLimit(requestURI, authHeader);
        
        if (result.isAllowed()) {
            // Request allowed
            response.addHeader("X-Rate-Limit-Remaining", String.valueOf(result.getRemainingTokens()));
            log.debug("Request allowed - remaining tokens: {}", result.getRemainingTokens());
            filterChain.doFilter(request, response);
        } else {
            // Rate limit exceeded
            response.addHeader("X-Rate-Limit-Retry-After-Seconds", String.valueOf(result.getWaitForRefillSeconds()));
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.getWriter().write("Rate limit exceeded. Try again in " + result.getWaitForRefillSeconds() + " seconds.");
            
            log.warn("Rate limit exceeded for {} {} from IP: {}", method, requestURI, getClientIP(request));
        }
    }

    private String getClientIP(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String requestURI = request.getRequestURI();
        // Skip rate limiting for health checks and static resources
        return requestURI.startsWith("/actuator/") || 
               requestURI.startsWith("/v3/api-docs") ||
               requestURI.startsWith("/swagger-ui") ||
               requestURI.equals("/api/v1/health") ||
               requestURI.equals("/api/v1/system/status");
    }
} 