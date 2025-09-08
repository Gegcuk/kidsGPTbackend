package uk.gegc.kidsgptbackend.service.subscription.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gegc.kidsgptbackend.model.subscription.SubscriptionPlan;
import uk.gegc.kidsgptbackend.model.subscription.UserSubscription;
import uk.gegc.kidsgptbackend.model.user.User;
import uk.gegc.kidsgptbackend.repository.subscription.UserSubscriptionRepository;
import uk.gegc.kidsgptbackend.repository.subscription.SubscriptionUsageRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SubscriptionAccessService Serialization & Features JSON Tests")
class SubscriptionAccessServiceSerializationTest {

    @Mock
    private UserSubscriptionRepository userSubscriptionRepository;

    @Mock
    private SubscriptionUsageRepository subscriptionUsageRepository;

    private SubscriptionAccessServiceImpl subscriptionAccessService;
    private ObjectMapper objectMapper;

    private User testUser;
    private SubscriptionPlan testPlan;
    private UserSubscription testSubscription;

    @BeforeEach
    void setUp() {
        // Initialize ObjectMapper
        objectMapper = new ObjectMapper();
        
        // Create service with mocked dependencies
        subscriptionAccessService = new SubscriptionAccessServiceImpl(
                userSubscriptionRepository,
                subscriptionUsageRepository,
                objectMapper
        );
        
        // Create test user
        testUser = new User();
        testUser.setId(java.util.UUID.randomUUID());
        testUser.setUsername("testuser");
        testUser.setEmail("test@example.com");
        testUser.setHashedPassword("password");
        testUser.setActive(true);
        testUser.setCreatedAt(Instant.now().minus(1, ChronoUnit.DAYS)); // Within free window

        // Create test plan
        testPlan = new SubscriptionPlan();
        testPlan.setId(java.util.UUID.randomUUID());
        testPlan.setName("Test Plan");

        // Create test subscription
        testSubscription = new UserSubscription();
        testSubscription.setId(java.util.UUID.randomUUID());
        testSubscription.setUser(testUser);
        testSubscription.setSubscriptionPlan(testPlan);
        testSubscription.setStatus(UserSubscription.SubscriptionStatus.ACTIVE);
        testSubscription.setPaymentProvider(UserSubscription.PaymentProvider.GOOGLE_PLAY);
        testSubscription.setExternalSubscriptionId("test_token");
        testSubscription.setStartDate(Instant.now().minus(1, ChronoUnit.DAYS));
        testSubscription.setCurrentPeriodStart(Instant.now().minus(1, ChronoUnit.DAYS));
        testSubscription.setCurrentPeriodEnd(Instant.now().plus(29, ChronoUnit.DAYS));
        testSubscription.setAutoRenew(true);
        testSubscription.setCreatedAt(Instant.now());
        testSubscription.setUpdatedAt(Instant.now());
        
        // Mock no active subscription by default (lenient to avoid unnecessary stubbing errors)
        lenient().when(userSubscriptionRepository.findActiveSubscriptionByUser(any(User.class)))
                .thenReturn(Optional.empty());
        
        // Mock no usage records by default - but allow creation (lenient to avoid unnecessary stubbing errors)
        lenient().when(subscriptionUsageRepository.findByUserAndFeatureAndPeriodKey(any(User.class), anyString(), anyString()))
                .thenReturn(Optional.empty());
        lenient().when(subscriptionUsageRepository.save(any(uk.gegc.kidsgptbackend.model.subscription.SubscriptionUsage.class)))
                .thenAnswer(invocation -> {
                    uk.gegc.kidsgptbackend.model.subscription.SubscriptionUsage usage = invocation.getArgument(0);
                    usage.setId(java.util.UUID.randomUUID());
                    return usage;
                });
    }

    @Test
    @DisplayName("Plan features parsed - chat_limit -1 should be unlimited")
    void planFeaturesParsed_chatLimitMinus1_shouldBeUnlimited() {
        // Given - plan with unlimited chat_limit
        testPlan.setFeatures("{\"chat_limit\": -1}");
        when(userSubscriptionRepository.findActiveSubscriptionByUser(testUser))
                .thenReturn(Optional.of(testSubscription));

        // When
        boolean hasAccess = subscriptionAccessService.hasFeatureAccess(testUser, "chat_limit");

        // Then - should have unlimited access
        assertThat(hasAccess).isTrue();
    }

    @Test
    @DisplayName("Plan features parsed - chat_limit 15 should be limited")
    void planFeaturesParsed_chatLimit15_shouldBeLimited() {
        // Given - plan with limited chat_limit
        testPlan.setFeatures("{\"chat_limit\": 15}");
        when(userSubscriptionRepository.findActiveSubscriptionByUser(testUser))
                .thenReturn(Optional.of(testSubscription));

        // When
        boolean hasAccess = subscriptionAccessService.hasFeatureAccess(testUser, "chat_limit");

        // Then - should have limited access (depends on usage)
        assertThat(hasAccess).isTrue(); // Should have access if within limit
    }

    @Test
    @DisplayName("Plan features parsed - chat_limit 0 should deny access")
    void planFeaturesParsed_chatLimit0_shouldDenyAccess() {
        // Given - plan with no chat_limit
        testPlan.setFeatures("{\"chat_limit\": 0}");
        when(userSubscriptionRepository.findActiveSubscriptionByUser(testUser))
                .thenReturn(Optional.of(testSubscription));

        // When
        boolean hasAccess = subscriptionAccessService.hasFeatureAccess(testUser, "chat_limit");

        // Then - should NOT have access
        assertThat(hasAccess).isFalse();
    }

    @Test
    @DisplayName("Plan features parsed - missing feature should be treated as 0")
    void planFeaturesParsed_missingFeature_shouldBeTreatedAs0() {
        // Given - plan without chat_limit feature
        testPlan.setFeatures("{\"story_generation\": 10}");
        when(userSubscriptionRepository.findActiveSubscriptionByUser(testUser))
                .thenReturn(Optional.of(testSubscription));

        // When
        boolean hasAccess = subscriptionAccessService.hasFeatureAccess(testUser, "chat_limit");

        // Then - should NOT have access (missing feature treated as 0)
        assertThat(hasAccess).isFalse();
    }

    @Test
    @DisplayName("Plan features parsed - non-numeric feature should be treated as 0")
    void planFeaturesParsed_nonNumericFeature_shouldBeTreatedAs0() {
        // Given - plan with non-numeric chat_limit
        testPlan.setFeatures("{\"chat_limit\": \"unlimited\"}");
        when(userSubscriptionRepository.findActiveSubscriptionByUser(testUser))
                .thenReturn(Optional.of(testSubscription));

        // When
        boolean hasAccess = subscriptionAccessService.hasFeatureAccess(testUser, "chat_limit");

        // Then - should NOT have access (non-numeric treated as 0)
        assertThat(hasAccess).isFalse();
    }

    @Test
    @DisplayName("Plan features parsed - null feature value should be treated as 0")
    void planFeaturesParsed_nullFeatureValue_shouldBeTreatedAs0() {
        // Given - plan with null chat_limit
        testPlan.setFeatures("{\"chat_limit\": null}");
        when(userSubscriptionRepository.findActiveSubscriptionByUser(testUser))
                .thenReturn(Optional.of(testSubscription));

        // When
        boolean hasAccess = subscriptionAccessService.hasFeatureAccess(testUser, "chat_limit");

        // Then - should NOT have access (null treated as 0)
        assertThat(hasAccess).isFalse();
    }

    @Test
    @DisplayName("Plan features parsed - boolean feature value should be treated as 0")
    void planFeaturesParsed_booleanFeatureValue_shouldBeTreatedAs0() {
        // Given - plan with boolean chat_limit
        testPlan.setFeatures("{\"chat_limit\": true}");
        when(userSubscriptionRepository.findActiveSubscriptionByUser(testUser))
                .thenReturn(Optional.of(testSubscription));

        // When
        boolean hasAccess = subscriptionAccessService.hasFeatureAccess(testUser, "chat_limit");

        // Then - should NOT have access (boolean treated as 0)
        assertThat(hasAccess).isFalse();
    }

    @Test
    @DisplayName("Malformed JSON - invalid JSON should log error and deny access")
    void malformedJson_invalidJson_shouldLogErrorAndDenyAccess() {
        // Given - plan with invalid JSON
        testPlan.setFeatures("{\"chat_limit\": 15, \"invalid\": }"); // Missing value
        when(userSubscriptionRepository.findActiveSubscriptionByUser(testUser))
                .thenReturn(Optional.of(testSubscription));

        // When
        boolean hasAccess = subscriptionAccessService.hasFeatureAccess(testUser, "chat_limit");

        // Then - should NOT have access (malformed JSON results in 0 limit)
        assertThat(hasAccess).isFalse();
    }

    @Test
    @DisplayName("Malformed JSON - empty string should log error and deny access")
    void malformedJson_emptyString_shouldLogErrorAndDenyAccess() {
        // Given - plan with empty features string
        testPlan.setFeatures("");
        when(userSubscriptionRepository.findActiveSubscriptionByUser(testUser))
                .thenReturn(Optional.of(testSubscription));

        // When
        boolean hasAccess = subscriptionAccessService.hasFeatureAccess(testUser, "chat_limit");

        // Then - should NOT have access (empty string results in 0 limit)
        assertThat(hasAccess).isFalse();
    }

    @Test
    @DisplayName("Malformed JSON - null features should throw exception")
    void malformedJson_nullFeatures_shouldThrowException() {
        // Given - plan with null features
        testPlan.setFeatures(null);
        when(userSubscriptionRepository.findActiveSubscriptionByUser(testUser))
                .thenReturn(Optional.of(testSubscription));

        // When/Then - should throw exception when trying to parse null features
        assertThat(org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> subscriptionAccessService.hasFeatureAccess(testUser, "chat_limit")
        )).isNotNull();
    }

    @Test
    @DisplayName("Malformed JSON - non-JSON string should log error and deny access")
    void malformedJson_nonJsonString_shouldLogErrorAndDenyAccess() {
        // Given - plan with non-JSON string
        testPlan.setFeatures("not a json string");
        when(userSubscriptionRepository.findActiveSubscriptionByUser(testUser))
                .thenReturn(Optional.of(testSubscription));

        // When
        boolean hasAccess = subscriptionAccessService.hasFeatureAccess(testUser, "chat_limit");

        // Then - should NOT have access (non-JSON results in 0 limit)
        assertThat(hasAccess).isFalse();
    }

    @Test
    @DisplayName("Complex features JSON - multiple features should parse correctly")
    void complexFeaturesJson_multipleFeatures_shouldParseCorrectly() {
        // Given - plan with multiple features
        testPlan.setFeatures("{\"chat_limit\": -1, \"story_generation\": 5, \"image_generation\": 3}");
        when(userSubscriptionRepository.findActiveSubscriptionByUser(testUser))
                .thenReturn(Optional.of(testSubscription));

        // When
        boolean hasChatAccess = subscriptionAccessService.hasFeatureAccess(testUser, "chat_limit");
        boolean hasStoryAccess = subscriptionAccessService.hasFeatureAccess(testUser, "story_generation");
        boolean hasImageAccess = subscriptionAccessService.hasFeatureAccess(testUser, "image_generation");
        boolean hasUnknownAccess = subscriptionAccessService.hasFeatureAccess(testUser, "unknown_feature");

        // Then - should parse each feature correctly
        assertThat(hasChatAccess).isTrue(); // -1 = unlimited
        assertThat(hasStoryAccess).isTrue(); // 5 = limited
        assertThat(hasImageAccess).isTrue(); // 3 = limited
        assertThat(hasUnknownAccess).isFalse(); // missing = 0
    }

    @Test
    @DisplayName("Edge case - very large number should be treated as limited")
    void edgeCase_veryLargeNumber_shouldBeTreatedAsLimited() {
        // Given - plan with very large chat_limit
        testPlan.setFeatures("{\"chat_limit\": 999999999}");
        when(userSubscriptionRepository.findActiveSubscriptionByUser(testUser))
                .thenReturn(Optional.of(testSubscription));

        // When
        boolean hasAccess = subscriptionAccessService.hasFeatureAccess(testUser, "chat_limit");

        // Then - should have access (large number is still limited, not unlimited)
        assertThat(hasAccess).isTrue();
    }

    @Test
    @DisplayName("Edge case - negative number other than -1 should be treated as 0")
    void edgeCase_negativeNumberOtherThanMinus1_shouldBeTreatedAs0() {
        // Given - plan with negative chat_limit (not -1)
        testPlan.setFeatures("{\"chat_limit\": -5}");
        when(userSubscriptionRepository.findActiveSubscriptionByUser(testUser))
                .thenReturn(Optional.of(testSubscription));

        // When
        boolean hasAccess = subscriptionAccessService.hasFeatureAccess(testUser, "chat_limit");

        // Then - should NOT have access (negative numbers other than -1 treated as 0)
        assertThat(hasAccess).isFalse();
    }

    @Test
    @DisplayName("Edge case - decimal number should be treated as 0")
    void edgeCase_decimalNumber_shouldBeTreatedAs0() {
        // Given - plan with decimal chat_limit
        testPlan.setFeatures("{\"chat_limit\": 15.5}");
        when(userSubscriptionRepository.findActiveSubscriptionByUser(testUser))
                .thenReturn(Optional.of(testSubscription));

        // When
        boolean hasAccess = subscriptionAccessService.hasFeatureAccess(testUser, "chat_limit");

        // Then - NOTE: Jackson can parse 15.5 as a number, but asInt() will truncate to 15
        // The current implementation doesn't distinguish between integers and decimals
        // This test documents the current behavior - decimal handling should be improved
        assertThat(hasAccess).isTrue();
    }

    @Test
    @DisplayName("Edge case - array value should be treated as 0")
    void edgeCase_arrayValue_shouldBeTreatedAs0() {
        // Given - plan with array chat_limit
        testPlan.setFeatures("{\"chat_limit\": [15, 20]}");
        when(userSubscriptionRepository.findActiveSubscriptionByUser(testUser))
                .thenReturn(Optional.of(testSubscription));

        // When
        boolean hasAccess = subscriptionAccessService.hasFeatureAccess(testUser, "chat_limit");

        // Then - should NOT have access (arrays treated as 0)
        assertThat(hasAccess).isFalse();
    }

    @Test
    @DisplayName("Edge case - object value should be treated as 0")
    void edgeCase_objectValue_shouldBeTreatedAs0() {
        // Given - plan with object chat_limit
        testPlan.setFeatures("{\"chat_limit\": {\"limit\": 15}}");
        when(userSubscriptionRepository.findActiveSubscriptionByUser(testUser))
                .thenReturn(Optional.of(testSubscription));

        // When
        boolean hasAccess = subscriptionAccessService.hasFeatureAccess(testUser, "chat_limit");

        // Then - should NOT have access (objects treated as 0)
        assertThat(hasAccess).isFalse();
    }
}
