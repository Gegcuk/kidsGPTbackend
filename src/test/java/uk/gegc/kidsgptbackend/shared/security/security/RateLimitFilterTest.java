package uk.gegc.kidsgptbackend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Execution(ExecutionMode.CONCURRENT)
public class RateLimitFilterTest {

    @Mock
    private RateLimitService rateLimitService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @Mock
    private PrintWriter printWriter;

    private RateLimitFilter rateLimitFilter;
    private StringWriter stringWriter;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        rateLimitFilter = new RateLimitFilter(Optional.of(rateLimitService));
        // Set rateLimitEnabled to true for tests that need it
        ReflectionTestUtils.setField(rateLimitFilter, "rateLimitEnabled", true);
        stringWriter = new StringWriter();
        printWriter = new PrintWriter(stringWriter);
    }

    @Test
    @DisplayName("Should allow request when rate limit is not exceeded")
    void shouldAllowRequest_whenRateLimitNotExceeded() throws Exception {
        // Given
        when(request.getRequestURI()).thenReturn("/api/v1/auth/login");
        when(request.getMethod()).thenReturn("POST");
        when(request.getHeader("Authorization")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        RateLimitService.RateLimitResult result = RateLimitService.RateLimitResult.builder()
                .allowed(true)
                .remainingTokens(4)
                .waitForRefillSeconds(0)
                .build();

        when(rateLimitService.checkRateLimit(eq("/api/v1/auth/login"), isNull())).thenReturn(result);

        // When
        rateLimitFilter.doFilterInternal(request, response, filterChain);

        // Then
        verify(filterChain).doFilter(request, response);
        verify(response).addHeader("X-Rate-Limit-Remaining", "4");
        verify(response, never()).setStatus(anyInt());
    }

    @Test
    @DisplayName("Should block request when rate limit is exceeded")
    void shouldBlockRequest_whenRateLimitExceeded() throws Exception {
        // Given
        when(request.getRequestURI()).thenReturn("/api/v1/auth/login");
        when(request.getMethod()).thenReturn("POST");
        when(request.getHeader("Authorization")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(response.getWriter()).thenReturn(printWriter);

        RateLimitService.RateLimitResult result = RateLimitService.RateLimitResult.builder()
                .allowed(false)
                .remainingTokens(0)
                .waitForRefillSeconds(30)
                .build();

        when(rateLimitService.checkRateLimit(eq("/api/v1/auth/login"), isNull())).thenReturn(result);

        // When
        rateLimitFilter.doFilterInternal(request, response, filterChain);

        // Then
        verify(filterChain, never()).doFilter(request, response);
        verify(response).setStatus(429);
        verify(response).addHeader("X-Rate-Limit-Retry-After-Seconds", "30");
        assertThat(stringWriter.toString()).contains("Rate limit exceeded");
    }

    @Test
    @DisplayName("Should skip rate limiting when service is not available")
    void shouldSkipRateLimiting_whenServiceNotAvailable() throws Exception {
        // Given
        RateLimitFilter filterWithoutService = new RateLimitFilter(Optional.empty());
        ReflectionTestUtils.setField(filterWithoutService, "rateLimitEnabled", true);
        when(request.getRequestURI()).thenReturn("/api/v1/auth/login");

        // When
        filterWithoutService.doFilterInternal(request, response, filterChain);

        // Then
        verify(filterChain).doFilter(request, response);
        verify(response, never()).addHeader(anyString(), anyString());
        verifyNoInteractions(rateLimitService);
    }

    @Test
    @DisplayName("Should skip rate limiting when disabled")
    void shouldSkipRateLimiting_whenDisabled() throws Exception {
        // Given
        ReflectionTestUtils.setField(rateLimitFilter, "rateLimitEnabled", false);
        when(request.getRequestURI()).thenReturn("/api/v1/auth/login");

        // When
        rateLimitFilter.doFilterInternal(request, response, filterChain);

        // Then
        verify(filterChain).doFilter(request, response);
        verify(response, never()).addHeader(anyString(), anyString());
        verifyNoInteractions(rateLimitService);
    }

    @Test
    @DisplayName("Should skip rate limiting for health check endpoints")
    void shouldSkipRateLimiting_forHealthCheckEndpoints() throws Exception {
        // Given
        when(request.getRequestURI()).thenReturn("/api/v1/health");

        // When
        boolean shouldFilter = rateLimitFilter.shouldNotFilter(request);

        // Then
        assertThat(shouldFilter).isTrue();
    }

    @Test
    @DisplayName("Should skip rate limiting for actuator endpoints")
    void shouldSkipRateLimiting_forActuatorEndpoints() throws Exception {
        // Given
        when(request.getRequestURI()).thenReturn("/actuator/health");

        // When
        boolean shouldFilter = rateLimitFilter.shouldNotFilter(request);

        // Then
        assertThat(shouldFilter).isTrue();
    }

    @Test
    @DisplayName("Should apply rate limiting for chat endpoints")
    void shouldApplyRateLimiting_forChatEndpoints() throws Exception {
        // Given
        when(request.getRequestURI()).thenReturn("/api/v1/chat");
        when(request.getMethod()).thenReturn("POST");
        when(request.getHeader("Authorization")).thenReturn("Bearer valid-token");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        RateLimitService.RateLimitResult result = RateLimitService.RateLimitResult.builder()
                .allowed(true)
                .remainingTokens(29)
                .waitForRefillSeconds(0)
                .build();

        when(rateLimitService.checkRateLimit(eq("/api/v1/chat"), eq("Bearer valid-token"))).thenReturn(result);

        // When
        rateLimitFilter.doFilterInternal(request, response, filterChain);

        // Then
        verify(filterChain).doFilter(request, response);
        verify(response).addHeader("X-Rate-Limit-Remaining", "29");
    }
} 