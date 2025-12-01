package uk.gegc.kidsgptbackend.features.consent.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentPolicies;
import uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType;
import uk.gegc.kidsgptbackend.features.consent.domain.repository.ConsentPoliciesRepository;
import uk.gegc.kidsgptbackend.test.BaseRepositoryTest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ConsentPolicies Entity Tests")
class ConsentPoliciesEntityTest extends BaseRepositoryTest {

    @Autowired
    private ConsentPoliciesRepository consentPoliciesRepository;

    private ConsentType testPolicyType;

    @Override
    @BeforeEach
    protected void setUp() {
        super.setUp();
        testPolicyType = ConsentType.PRIVACY_POLICY;
    }

    @Test
    @DisplayName("onCreate: should auto-populate isActive to false when null")
    void onCreate_shouldAutoPopulateIsActiveToFalse() {
        // Given
        ConsentPolicies policy = ConsentPolicies.builder()
                .policyType(testPolicyType)
                .version("1.0.0")
                .effectiveDate(LocalDate.now())
                .contentHash("hash123")
                .policyUrl("https://example.com/policy")
                .isActive(null) // Should be auto-populated
                .build();

        // When
        ConsentPolicies saved = persistFlushAndClear(policy);
        Optional<ConsentPolicies> found = consentPoliciesRepository.findById(saved.getPolicyId());

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getIsActive()).isFalse();
    }

    @Test
    @DisplayName("onCreate: should preserve isActive when explicitly set")
    void onCreate_shouldPreserveIsActiveWhenSet() {
        // Given
        ConsentPolicies policy = ConsentPolicies.builder()
                .policyType(testPolicyType)
                .version("1.0.0")
                .effectiveDate(LocalDate.now())
                .contentHash("hash123")
                .policyUrl("https://example.com/policy")
                .isActive(true) // Explicitly set
                .build();

        // When
        ConsentPolicies saved = persistFlushAndClear(policy);
        Optional<ConsentPolicies> found = consentPoliciesRepository.findById(saved.getPolicyId());

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getIsActive()).isTrue();
    }

    @Test
    @DisplayName("onCreate: should auto-populate createdAt when null")
    void onCreate_shouldAutoPopulateCreatedAt() {
        // Given
        ConsentPolicies policy = ConsentPolicies.builder()
                .policyType(testPolicyType)
                .version("1.0.0")
                .effectiveDate(LocalDate.now())
                .contentHash("hash123")
                .policyUrl("https://example.com/policy")
                .isActive(true)
                .createdAt(null) // Should be auto-populated
                .build();

        // When
        ConsentPolicies saved = persistFlushAndClear(policy);
        Optional<ConsentPolicies> found = consentPoliciesRepository.findById(saved.getPolicyId());

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getCreatedAt()).isNotNull();
        assertThat(found.get().getCreatedAt()).isBefore(LocalDateTime.now(ZoneOffset.UTC).plusSeconds(5));
    }

    @Test
    @DisplayName("onCreate: should preserve createdAt when explicitly set")
    void onCreate_shouldPreserveCreatedAtWhenSet() {
        // Given
        LocalDateTime fixedTime = LocalDateTime.of(2024, 1, 1, 12, 0, 0);
        ConsentPolicies policy = ConsentPolicies.builder()
                .policyType(testPolicyType)
                .version("1.0.0")
                .effectiveDate(LocalDate.now())
                .contentHash("hash123")
                .policyUrl("https://example.com/policy")
                .isActive(true)
                .createdAt(fixedTime) // Explicitly set
                .build();

        // When
        ConsentPolicies saved = persistFlushAndClear(policy);
        Optional<ConsentPolicies> found = consentPoliciesRepository.findById(saved.getPolicyId());

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getCreatedAt()).isEqualTo(fixedTime);
    }

    @Test
    @DisplayName("onCreate: should handle both isActive and createdAt null")
    void onCreate_shouldHandleBothNull() {
        // Given
        ConsentPolicies policy = ConsentPolicies.builder()
                .policyType(testPolicyType)
                .version("1.0.0")
                .effectiveDate(LocalDate.now())
                .contentHash("hash123")
                .policyUrl("https://example.com/policy")
                .isActive(null)
                .createdAt(null)
                .build();

        // When
        ConsentPolicies saved = persistFlushAndClear(policy);
        Optional<ConsentPolicies> found = consentPoliciesRepository.findById(saved.getPolicyId());

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getIsActive()).isFalse();
        assertThat(found.get().getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("save: should persist all fields correctly")
    void save_shouldPersistAllFields() {
        // Given
        LocalDate effectiveDate = LocalDate.of(2024, 1, 1);
        LocalDateTime createdAt = LocalDateTime.of(2024, 1, 1, 12, 0, 0);
        ConsentPolicies policy = ConsentPolicies.builder()
                .policyType(ConsentType.TERMS_OF_SERVICE)
                .version("2.0.0")
                .effectiveDate(effectiveDate)
                .contentHash("content-hash-123")
                .policyUrl("https://example.com/terms")
                .locale("en-GB")
                .isActive(true)
                .createdAt(createdAt)
                .build();

        // When
        ConsentPolicies saved = persistFlushAndClear(policy);
        Optional<ConsentPolicies> found = consentPoliciesRepository.findById(saved.getPolicyId());

        // Then
        assertThat(found).isPresent();
        ConsentPolicies retrieved = found.get();
        assertThat(retrieved.getPolicyId()).isNotNull();
        assertThat(retrieved.getPolicyType()).isEqualTo(ConsentType.TERMS_OF_SERVICE);
        assertThat(retrieved.getVersion()).isEqualTo("2.0.0");
        assertThat(retrieved.getEffectiveDate()).isEqualTo(effectiveDate);
        assertThat(retrieved.getContentHash()).isEqualTo("content-hash-123");
        assertThat(retrieved.getPolicyUrl()).isEqualTo("https://example.com/terms");
        assertThat(retrieved.getLocale()).isEqualTo("en-GB");
        assertThat(retrieved.getIsActive()).isTrue();
        assertThat(retrieved.getCreatedAt()).isEqualTo(createdAt);
    }

    @Test
    @DisplayName("save: should handle null locale")
    void save_shouldHandleNullLocale() {
        // Given
        ConsentPolicies policy = ConsentPolicies.builder()
                .policyType(testPolicyType)
                .version("1.0.0")
                .effectiveDate(LocalDate.now())
                .contentHash("hash123")
                .policyUrl("https://example.com/policy")
                .locale(null)
                .isActive(true)
                .build();

        // When
        ConsentPolicies saved = persistFlushAndClear(policy);
        Optional<ConsentPolicies> found = consentPoliciesRepository.findById(saved.getPolicyId());

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getLocale()).isNull();
    }
}

