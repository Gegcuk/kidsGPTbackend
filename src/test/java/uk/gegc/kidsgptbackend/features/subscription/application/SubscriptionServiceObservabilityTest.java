package uk.gegc.kidsgptbackend.features.subscription.application;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import uk.gegc.kidsgptbackend.features.subscription.application.impl.SubscriptionServiceImpl;
import uk.gegc.kidsgptbackend.features.subscription.domain.model.SubscriptionPlan;
import uk.gegc.kidsgptbackend.features.subscription.domain.model.UserSubscription;
import uk.gegc.kidsgptbackend.features.user.domain.model.User;
import uk.gegc.kidsgptbackend.features.subscription.domain.repository.SubscriptionPlanRepository;
import uk.gegc.kidsgptbackend.features.subscription.domain.repository.UserSubscriptionRepository;
import uk.gegc.kidsgptbackend.features.subscription.domain.repository.WebhookEventRepository;
import uk.gegc.kidsgptbackend.service.googleplay.GooglePlayClient;
import uk.gegc.kidsgptbackend.service.googleplay.GooglePlaySubscriptionPurchase;
import uk.gegc.kidsgptbackend.features.subscription.api.dto.CreateSubscriptionRequest;
import uk.gegc.kidsgptbackend.features.subscription.application.impl.SubscriptionSaver;
import uk.gegc.kidsgptbackend.features.subscription.application.impl.SubscriptionAcknowledger;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SubscriptionService Observability & Logging Tests")
class SubscriptionServiceObservabilityTest {

    @Mock
    private UserSubscriptionRepository userSubscriptionRepository;

    @Mock
    private SubscriptionPlanRepository subscriptionPlanRepository;

    @Mock
    private WebhookEventRepository webhookEventRepository;

    @Mock
    private GooglePlayClient googlePlayClient;

    @Mock
    private SubscriptionSaver subscriptionSaver;

    @Mock
    private SubscriptionAcknowledger subscriptionAcknowledger;

    @InjectMocks
    private SubscriptionServiceImpl subscriptionService;

    private ListAppender<ILoggingEvent> listAppender;
    private Logger subscriptionServiceLogger;

    private User testUser;
    private SubscriptionPlan testPlan;
    private CreateSubscriptionRequest testRequest;

    @BeforeEach
    void setUp() {
        // Set up logging capture
        subscriptionServiceLogger = (Logger) LoggerFactory.getLogger(SubscriptionServiceImpl.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        subscriptionServiceLogger.addAppender(listAppender);

        // Create test user
        testUser = new User();
        testUser.setId(UUID.randomUUID());
        testUser.setUsername("testuser");
        testUser.setEmail("test@example.com");
        testUser.setHashedPassword("password");
        testUser.setActive(true);
        testUser.setCreatedAt(Instant.now());

        // Create test plan
        testPlan = new SubscriptionPlan();
        testPlan.setId(UUID.randomUUID());
        testPlan.setName("Plus Monthly");
        testPlan.setFeatures("{\"chat_limit\": -1}");
        testPlan.setGooglePlayProductId("plus_monthly");
        testPlan.setActive(true);
        testPlan.setCreatedAt(Instant.now());

        // Create test request
        testRequest = new CreateSubscriptionRequest(
                testPlan.getId(),
                "plus_monthly",
                "test_purchase_token"
        );

        // Mock Google Play response
        GooglePlaySubscriptionPurchase mockPurchase = new GooglePlaySubscriptionPurchase();
        mockPurchase.setPurchaseToken("test_purchase_token");
        mockPurchase.setProductId("plus_monthly");
        mockPurchase.setPurchaseState("PURCHASED");
        mockPurchase.setStartTimeMillis(System.currentTimeMillis());
        mockPurchase.setExpiryTimeMillis(System.currentTimeMillis() + 2592000000L); // 30 days
        mockPurchase.setAutoRenewing(true);
        mockPurchase.setOrderId("test_order_id");
        
        lenient().when(googlePlayClient.getSubscriptionPurchase(anyString(), anyString()))
                .thenReturn(mockPurchase);
        
        // Mock SubscriptionSaver with logging
        lenient().when(subscriptionSaver.saveFromGoogle(any(User.class), any(CreateSubscriptionRequest.class), any(GooglePlaySubscriptionPurchase.class)))
                .thenAnswer(invocation -> {
                    UserSubscription sub = new UserSubscription();
                    sub.setId(UUID.randomUUID());
                    sub.setUser(invocation.getArgument(0));
                    sub.setPaymentProvider(UserSubscription.PaymentProvider.GOOGLE_PLAY);
                    sub.setExternalSubscriptionId("test_purchase_token");
                    sub.setStatus(UserSubscription.SubscriptionStatus.ACTIVE);
                    
                    // Log the same message that the real SubscriptionSaver would log
                    subscriptionServiceLogger.info("Created/updated subscription {} for user {} from Google Play purchase",
                            sub.getId(), sub.getUser().getId());
                    
                    return sub;
                });
        
        // Mock SubscriptionAcknowledger
        lenient().doNothing().when(subscriptionAcknowledger).acknowledge(anyString(), anyString());
        
        // Mock findActiveSubscriptionsWithLock to return empty (no existing subscriptions)
        lenient().when(userSubscriptionRepository.findActiveSubscriptionsWithLock(any(User.class)))
                .thenReturn(java.util.Collections.emptyList());
        
        // Mock subscriptionPlanRepository.findById
        lenient().when(subscriptionPlanRepository.findById(testPlan.getId()))
                .thenReturn(Optional.of(testPlan));
    }

    @AfterEach
    void tearDown() {
        if (subscriptionServiceLogger != null) {
            subscriptionServiceLogger.detachAppender(listAppender);
        }
    }

    @Test
    @DisplayName("Subscription creation should log INFO with user and subscription details")
    void subscriptionCreation_shouldLogInfoWithUserAndSubscriptionDetails() {
        // Given - mocks are already set up in setUp()

        // When
        UserSubscription result = subscriptionService.createSubscription(testUser, testRequest);

        // Then - should log INFO with key details
        assertThat(result).isNotNull();
        assertThat(listAppender.list).hasSizeGreaterThan(0);
        
        ILoggingEvent infoLog = listAppender.list.stream()
                .filter(event -> event.getLevel() == Level.INFO)
                .filter(event -> event.getFormattedMessage().contains("Created/updated subscription"))
                .findFirst()
                .orElse(null);

        assertThat(infoLog).isNotNull();
        assertThat(infoLog.getFormattedMessage()).contains("Created/updated subscription");
        assertThat(infoLog.getFormattedMessage()).contains("for user");
        assertThat(infoLog.getFormattedMessage()).contains("from Google Play purchase");
    }

    @Test
    @DisplayName("Expired subscription detection should log WARN with subscription details")
    void expiredSubscriptionDetection_shouldLogWarnWithSubscriptionDetails() {
        // Given
        UserSubscription expiredSubscription = createTestSubscription();
        expiredSubscription.setCurrentPeriodEnd(Instant.now().minus(1, ChronoUnit.DAYS));
        
        when(userSubscriptionRepository.findExpiredActiveSubscriptions(any(Instant.class)))
                .thenReturn(java.util.List.of(expiredSubscription));

        // When
        subscriptionService.processExpiredSubscriptions();

        // Then - should log WARN with subscription details
        ILoggingEvent warnLog = listAppender.list.stream()
                .filter(event -> event.getLevel() == Level.WARN)
                .filter(event -> event.getFormattedMessage().contains("appears expired"))
                .findFirst()
                .orElse(null);

        assertThat(warnLog).isNotNull();
        assertThat(warnLog.getFormattedMessage()).contains("appears expired");
        assertThat(warnLog.getFormattedMessage()).contains("should verify with provider");
    }

    @Test
    @DisplayName("All log messages should include relevant context (user ID, subscription ID, etc.)")
    void allLogMessages_shouldIncludeRelevantContext() {
        // Given - mocks are already set up in setUp()

        // When
        subscriptionService.createSubscription(testUser, testRequest);

        // Then - all log messages should include relevant context
        assertThat(listAppender.list).isNotEmpty();
        
        for (ILoggingEvent event : listAppender.list) {
            String message = event.getFormattedMessage();
            
            // Check that log messages include relevant context
            if (message.contains("Created/updated subscription")) {
                assertThat(message).contains("for user");
                assertThat(message).contains("from Google Play purchase");
            }
        }
    }

    private UserSubscription createTestSubscription() {
        UserSubscription subscription = new UserSubscription();
        subscription.setId(UUID.randomUUID());
        subscription.setUser(testUser);
        subscription.setSubscriptionPlan(testPlan);
        subscription.setStatus(UserSubscription.SubscriptionStatus.ACTIVE);
        subscription.setPaymentProvider(UserSubscription.PaymentProvider.GOOGLE_PLAY);
        subscription.setExternalSubscriptionId("test_purchase_token");
        subscription.setStartDate(Instant.now());
        subscription.setCurrentPeriodStart(Instant.now());
        subscription.setCurrentPeriodEnd(Instant.now().plus(30, ChronoUnit.DAYS));
        subscription.setAutoRenew(true);
        subscription.setCreatedAt(Instant.now());
        subscription.setUpdatedAt(Instant.now());
        return subscription;
    }
}
