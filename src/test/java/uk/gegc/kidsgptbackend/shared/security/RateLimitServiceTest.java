package uk.gegc.kidsgptbackend.shared.security;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import uk.gegc.kidsgptbackend.test.BaseUnitTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link RateLimitService}.
 * <p>
 * Tests rate limiting functionality including:
 * - Anonymous user rate limiting
 * - Authenticated user rate limiting
 * - Different endpoint buckets (register, auth, chat, general)
 * - Token extraction and validation
 * - Rate limit result building
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RateLimitService Tests")
class RateLimitServiceTest extends BaseUnitTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private Bucket authRateLimitBucket;

    @Mock
    private Bucket chatRateLimitBucket;

    @Mock
    private Bucket generalRateLimitBucket;

    @Mock
    private Bucket registerRateLimitBucket;

    @Mock
    private ConsumptionProbe consumptionProbe;

    @InjectMocks
    private RateLimitService rateLimitService;

    private static final String VALID_TOKEN = "valid.token.here";
    private static final String INVALID_TOKEN = "invalid.token";
    private static final String TEST_USERNAME = "testuser";

    @Override
    @BeforeEach
    protected void setUp() {
        super.setUp();
        
        // Inject mocked buckets using reflection
        ReflectionTestUtils.setField(rateLimitService, "authRateLimitBucket", authRateLimitBucket);
        ReflectionTestUtils.setField(rateLimitService, "chatRateLimitBucket", chatRateLimitBucket);
        ReflectionTestUtils.setField(rateLimitService, "generalRateLimitBucket", generalRateLimitBucket);
        ReflectionTestUtils.setField(rateLimitService, "registerRateLimitBucket", registerRateLimitBucket);
        ReflectionTestUtils.setField(rateLimitService, "chatRequestsPerMinute", 30);
    }

    @Test
    @DisplayName("checkRateLimit: when anonymous user and register endpoint then uses register bucket")
    void checkRateLimit_anonymousUserRegisterEndpoint_usesRegisterBucket() {
        // Given
        String requestURI = "/api/v1/auth/register";
        String authHeader = null;
        
        when(registerRateLimitBucket.tryConsumeAndReturnRemaining(1)).thenReturn(consumptionProbe);
        when(consumptionProbe.isConsumed()).thenReturn(true);
        when(consumptionProbe.getRemainingTokens()).thenReturn(10L);
        when(consumptionProbe.getNanosToWaitForRefill()).thenReturn(0L);

        // When
        RateLimitService.RateLimitResult result = rateLimitService.checkRateLimit(requestURI, authHeader);

        // Then
        assertThat(result.isAllowed()).isTrue();
        assertThat(result.getRemainingTokens()).isEqualTo(10L);
        assertThat(result.getWaitForRefillSeconds()).isEqualTo(0L);
        verify(registerRateLimitBucket).tryConsumeAndReturnRemaining(1);
        verify(chatRateLimitBucket, never()).tryConsumeAndReturnRemaining(anyInt());
        verify(authRateLimitBucket, never()).tryConsumeAndReturnRemaining(anyInt());
        verify(generalRateLimitBucket, never()).tryConsumeAndReturnRemaining(anyInt());
    }

    @Test
    @DisplayName("checkRateLimit: when anonymous user and auth endpoint then uses auth bucket")
    void checkRateLimit_anonymousUserAuthEndpoint_usesAuthBucket() {
        // Given
        String requestURI = "/api/v1/auth/login";
        String authHeader = null;
        
        when(authRateLimitBucket.tryConsumeAndReturnRemaining(1)).thenReturn(consumptionProbe);
        when(consumptionProbe.isConsumed()).thenReturn(true);
        when(consumptionProbe.getRemainingTokens()).thenReturn(5L);
        when(consumptionProbe.getNanosToWaitForRefill()).thenReturn(0L);

        // When
        RateLimitService.RateLimitResult result = rateLimitService.checkRateLimit(requestURI, authHeader);

        // Then
        assertThat(result.isAllowed()).isTrue();
        assertThat(result.getRemainingTokens()).isEqualTo(5L);
        verify(authRateLimitBucket).tryConsumeAndReturnRemaining(1);
        verify(registerRateLimitBucket, never()).tryConsumeAndReturnRemaining(anyInt());
    }

    @Test
    @DisplayName("checkRateLimit: when anonymous user and chat endpoint then uses chat bucket")
    void checkRateLimit_anonymousUserChatEndpoint_usesChatBucket() {
        // Given
        String requestURI = "/api/v1/chat";
        String authHeader = null;
        
        when(chatRateLimitBucket.tryConsumeAndReturnRemaining(1)).thenReturn(consumptionProbe);
        when(consumptionProbe.isConsumed()).thenReturn(true);
        when(consumptionProbe.getRemainingTokens()).thenReturn(20L);
        when(consumptionProbe.getNanosToWaitForRefill()).thenReturn(0L);

        // When
        RateLimitService.RateLimitResult result = rateLimitService.checkRateLimit(requestURI, authHeader);

        // Then
        assertThat(result.isAllowed()).isTrue();
        assertThat(result.getRemainingTokens()).isEqualTo(20L);
        verify(chatRateLimitBucket).tryConsumeAndReturnRemaining(1);
    }

    @Test
    @DisplayName("checkRateLimit: when anonymous user and general endpoint then uses general bucket")
    void checkRateLimit_anonymousUserGeneralEndpoint_usesGeneralBucket() {
        // Given
        String requestURI = "/api/v1/story";
        String authHeader = null;
        
        when(generalRateLimitBucket.tryConsumeAndReturnRemaining(1)).thenReturn(consumptionProbe);
        when(consumptionProbe.isConsumed()).thenReturn(true);
        when(consumptionProbe.getRemainingTokens()).thenReturn(15L);
        when(consumptionProbe.getNanosToWaitForRefill()).thenReturn(0L);

        // When
        RateLimitService.RateLimitResult result = rateLimitService.checkRateLimit(requestURI, authHeader);

        // Then
        assertThat(result.isAllowed()).isTrue();
        assertThat(result.getRemainingTokens()).isEqualTo(15L);
        verify(generalRateLimitBucket).tryConsumeAndReturnRemaining(1);
    }

    @Test
    @DisplayName("checkRateLimit: when authenticated user with valid token and chat endpoint then uses user bucket")
    void checkRateLimit_authenticatedUserChatEndpoint_usesUserBucket() {
        // Given
        String requestURI = "/api/v1/chat";
        String authHeader = "Bearer " + VALID_TOKEN;
        
        when(jwtTokenProvider.validateToken(VALID_TOKEN)).thenReturn(true);
        when(jwtTokenProvider.getUsername(VALID_TOKEN)).thenReturn(TEST_USERNAME);

        // When
        RateLimitService.RateLimitResult result = rateLimitService.checkRateLimit(requestURI, authHeader);

        // Then
        assertThat(result.isAllowed()).isTrue();
        assertThat(result.getRemainingTokens()).isGreaterThanOrEqualTo(0L);
        verify(jwtTokenProvider).validateToken(VALID_TOKEN);
        verify(jwtTokenProvider).getUsername(VALID_TOKEN);
        verify(chatRateLimitBucket, never()).tryConsumeAndReturnRemaining(anyInt());
    }

    @Test
    @DisplayName("checkRateLimit: when authenticated user with valid token and general endpoint then uses user bucket")
    void checkRateLimit_authenticatedUserGeneralEndpoint_usesUserBucket() {
        // Given
        String requestURI = "/api/v1/story";
        String authHeader = "Bearer " + VALID_TOKEN;
        
        when(jwtTokenProvider.validateToken(VALID_TOKEN)).thenReturn(true);
        when(jwtTokenProvider.getUsername(VALID_TOKEN)).thenReturn(TEST_USERNAME);
        
        // When
        RateLimitService.RateLimitResult result = rateLimitService.checkRateLimit(requestURI, authHeader);

        // Then
        assertThat(result.isAllowed()).isTrue();
        verify(jwtTokenProvider).validateToken(VALID_TOKEN);
        verify(jwtTokenProvider).getUsername(VALID_TOKEN);
        verify(generalRateLimitBucket, never()).tryConsumeAndReturnRemaining(anyInt());
    }

    @Test
    @DisplayName("checkRateLimit: when rate limit exceeded then returns not allowed with wait time")
    void checkRateLimit_rateLimitExceeded_returnsNotAllowedWithWaitTime() {
        // Given
        String requestURI = "/api/v1/chat";
        String authHeader = null;
        
        when(chatRateLimitBucket.tryConsumeAndReturnRemaining(1)).thenReturn(consumptionProbe);
        when(consumptionProbe.isConsumed()).thenReturn(false);
        when(consumptionProbe.getRemainingTokens()).thenReturn(0L);
        when(consumptionProbe.getNanosToWaitForRefill()).thenReturn(5_000_000_000L); // 5 seconds

        // When
        RateLimitService.RateLimitResult result = rateLimitService.checkRateLimit(requestURI, authHeader);

        // Then
        assertThat(result.isAllowed()).isFalse();
        assertThat(result.getRemainingTokens()).isEqualTo(0L);
        assertThat(result.getWaitForRefillSeconds()).isEqualTo(5L);
    }

    @Test
    @DisplayName("checkRateLimit: when invalid token then treats as anonymous")
    void checkRateLimit_invalidToken_treatsAsAnonymous() {
        // Given
        String requestURI = "/api/v1/chat";
        String authHeader = "Bearer " + INVALID_TOKEN;
        
        when(jwtTokenProvider.validateToken(INVALID_TOKEN)).thenReturn(false);
        when(chatRateLimitBucket.tryConsumeAndReturnRemaining(1)).thenReturn(consumptionProbe);
        when(consumptionProbe.isConsumed()).thenReturn(true);
        when(consumptionProbe.getRemainingTokens()).thenReturn(10L);
        when(consumptionProbe.getNanosToWaitForRefill()).thenReturn(0L);

        // When
        RateLimitService.RateLimitResult result = rateLimitService.checkRateLimit(requestURI, authHeader);

        // Then
        assertThat(result.isAllowed()).isTrue();
        verify(jwtTokenProvider).validateToken(INVALID_TOKEN);
        verify(chatRateLimitBucket).tryConsumeAndReturnRemaining(1);
    }

    @Test
    @DisplayName("checkRateLimit: when token validation throws exception then treats as anonymous")
    void checkRateLimit_tokenValidationException_treatsAsAnonymous() {
        // Given
        String requestURI = "/api/v1/chat";
        String authHeader = "Bearer " + INVALID_TOKEN;
        
        when(jwtTokenProvider.validateToken(INVALID_TOKEN)).thenThrow(new RuntimeException("Token expired"));
        when(chatRateLimitBucket.tryConsumeAndReturnRemaining(1)).thenReturn(consumptionProbe);
        when(consumptionProbe.isConsumed()).thenReturn(true);
        when(consumptionProbe.getRemainingTokens()).thenReturn(10L);
        when(consumptionProbe.getNanosToWaitForRefill()).thenReturn(0L);

        // When
        RateLimitService.RateLimitResult result = rateLimitService.checkRateLimit(requestURI, authHeader);

        // Then
        assertThat(result.isAllowed()).isTrue();
        verify(jwtTokenProvider).validateToken(INVALID_TOKEN);
        verify(chatRateLimitBucket).tryConsumeAndReturnRemaining(1);
    }

    @Test
    @DisplayName("checkRateLimit: when auth header without Bearer prefix then treats as anonymous")
    void checkRateLimit_authHeaderWithoutBearer_treatsAsAnonymous() {
        // Given
        String requestURI = "/api/v1/chat";
        String authHeader = "Invalid " + VALID_TOKEN;
        
        when(chatRateLimitBucket.tryConsumeAndReturnRemaining(1)).thenReturn(consumptionProbe);
        when(consumptionProbe.isConsumed()).thenReturn(true);
        when(consumptionProbe.getRemainingTokens()).thenReturn(10L);
        when(consumptionProbe.getNanosToWaitForRefill()).thenReturn(0L);

        // When
        RateLimitService.RateLimitResult result = rateLimitService.checkRateLimit(requestURI, authHeader);

        // Then
        assertThat(result.isAllowed()).isTrue();
        verify(jwtTokenProvider, never()).validateToken(anyString());
        verify(chatRateLimitBucket).tryConsumeAndReturnRemaining(1);
    }

    @Test
    @DisplayName("checkRateLimit: when same authenticated user makes multiple requests then uses same user bucket")
    void checkRateLimit_sameAuthenticatedUserMultipleRequests_usesSameUserBucket() {
        // Given
        String requestURI = "/api/v1/chat";
        String authHeader = "Bearer " + VALID_TOKEN;
        
        when(jwtTokenProvider.validateToken(VALID_TOKEN)).thenReturn(true);
        when(jwtTokenProvider.getUsername(VALID_TOKEN)).thenReturn(TEST_USERNAME);

        // When - first request
        RateLimitService.RateLimitResult result1 = rateLimitService.checkRateLimit(requestURI, authHeader);
        
        // When - second request
        RateLimitService.RateLimitResult result2 = rateLimitService.checkRateLimit(requestURI, authHeader);

        // Then
        assertThat(result1.isAllowed()).isTrue();
        assertThat(result2.isAllowed()).isTrue();
        // Both should use the same user bucket (remaining tokens should decrease)
        assertThat(result2.getRemainingTokens()).isLessThan(result1.getRemainingTokens());
        verify(jwtTokenProvider, times(2)).validateToken(VALID_TOKEN);
        verify(jwtTokenProvider, times(2)).getUsername(VALID_TOKEN);
    }

    @Test
    @DisplayName("checkRateLimit: when different authenticated users then uses different user buckets")
    void checkRateLimit_differentAuthenticatedUsers_usesDifferentUserBuckets() {
        // Given
        String requestURI = "/api/v1/chat";
        String authHeader1 = "Bearer token1";
        String authHeader2 = "Bearer token2";
        
        when(jwtTokenProvider.validateToken("token1")).thenReturn(true);
        when(jwtTokenProvider.getUsername("token1")).thenReturn("user1");
        when(jwtTokenProvider.validateToken("token2")).thenReturn(true);
        when(jwtTokenProvider.getUsername("token2")).thenReturn("user2");

        // When
        RateLimitService.RateLimitResult result1 = rateLimitService.checkRateLimit(requestURI, authHeader1);
        RateLimitService.RateLimitResult result2 = rateLimitService.checkRateLimit(requestURI, authHeader2);

        // Then
        assertThat(result1.isAllowed()).isTrue();
        assertThat(result2.isAllowed()).isTrue();
        // Both users should have full buckets (30 tokens each)
        assertThat(result1.getRemainingTokens()).isEqualTo(29L); // 30 - 1
        assertThat(result2.getRemainingTokens()).isEqualTo(29L); // 30 - 1, independent bucket
    }

    @Test
    @DisplayName("RateLimitResult builder: when all fields set then builds correctly")
    void rateLimitResultBuilder_allFieldsSet_buildsCorrectly() {
        // When
        RateLimitService.RateLimitResult result = RateLimitService.RateLimitResult.builder()
                .allowed(true)
                .remainingTokens(15L)
                .waitForRefillSeconds(10L)
                .build();

        // Then
        assertThat(result.isAllowed()).isTrue();
        assertThat(result.getRemainingTokens()).isEqualTo(15L);
        assertThat(result.getWaitForRefillSeconds()).isEqualTo(10L);
    }

    @Test
    @DisplayName("RateLimitResult builder: when rate limited then builds with correct values")
    void rateLimitResultBuilder_rateLimited_buildsWithCorrectValues() {
        // When
        RateLimitService.RateLimitResult result = RateLimitService.RateLimitResult.builder()
                .allowed(false)
                .remainingTokens(0L)
                .waitForRefillSeconds(30L)
                .build();

        // Then
        assertThat(result.isAllowed()).isFalse();
        assertThat(result.getRemainingTokens()).isEqualTo(0L);
        assertThat(result.getWaitForRefillSeconds()).isEqualTo(30L);
    }

    @Test
    @DisplayName("checkRateLimit: when authenticated user and register endpoint then uses register bucket")
    void checkRateLimit_authenticatedUserRegisterEndpoint_usesRegisterBucket() {
        // Given
        String requestURI = "/api/v1/auth/register";
        String authHeader = "Bearer " + VALID_TOKEN;
        
        when(jwtTokenProvider.validateToken(VALID_TOKEN)).thenReturn(true);
        when(jwtTokenProvider.getUsername(VALID_TOKEN)).thenReturn(TEST_USERNAME);
        when(registerRateLimitBucket.tryConsumeAndReturnRemaining(1)).thenReturn(consumptionProbe);
        when(consumptionProbe.isConsumed()).thenReturn(true);
        when(consumptionProbe.getRemainingTokens()).thenReturn(5L);
        when(consumptionProbe.getNanosToWaitForRefill()).thenReturn(0L);

        // When
        RateLimitService.RateLimitResult result = rateLimitService.checkRateLimit(requestURI, authHeader);

        // Then
        assertThat(result.isAllowed()).isTrue();
        // Register endpoint always uses register bucket, even for authenticated users
        verify(registerRateLimitBucket).tryConsumeAndReturnRemaining(1);
    }

    @Test
    @DisplayName("checkRateLimit: when authenticated user and auth endpoint then uses auth bucket")
    void checkRateLimit_authenticatedUserAuthEndpoint_usesAuthBucket() {
        // Given
        String requestURI = "/api/v1/auth/login";
        String authHeader = "Bearer " + VALID_TOKEN;
        
        when(jwtTokenProvider.validateToken(VALID_TOKEN)).thenReturn(true);
        when(jwtTokenProvider.getUsername(VALID_TOKEN)).thenReturn(TEST_USERNAME);
        when(authRateLimitBucket.tryConsumeAndReturnRemaining(1)).thenReturn(consumptionProbe);
        when(consumptionProbe.isConsumed()).thenReturn(true);
        when(consumptionProbe.getRemainingTokens()).thenReturn(3L);
        when(consumptionProbe.getNanosToWaitForRefill()).thenReturn(0L);

        // When
        RateLimitService.RateLimitResult result = rateLimitService.checkRateLimit(requestURI, authHeader);

        // Then
        assertThat(result.isAllowed()).isTrue();
        // Auth endpoint always uses auth bucket, even for authenticated users
        verify(authRateLimitBucket).tryConsumeAndReturnRemaining(1);
    }
}

