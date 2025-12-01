package uk.gegc.kidsgptbackend.features.consent.api.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType;
import uk.gegc.kidsgptbackend.test.BaseUnitTest;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ConsentPolicyResponse DTO Tests")
class ConsentPolicyResponseTest extends BaseUnitTest {

    @Test
    @DisplayName("should create response with policies")
    void createResponse_withPolicies_valid() {
        // Given
        ConsentPolicyResponse.PolicyInfo policy = new ConsentPolicyResponse.PolicyInfo(
                "policy1",
                ConsentType.PRIVACY_POLICY,
                "1.0",
                LocalDate.now(),
                "abc123",
                "https://example.com/policy",
                "en-GB",
                true
        );

        // When
        ConsentPolicyResponse response = new ConsentPolicyResponse(
                List.of(policy)
        );

        // Then
        assertThat(response.policies()).hasSize(1);
        assertThat(response.policies().get(0)).isEqualTo(policy);
    }

    @Test
    @DisplayName("should create response with empty policies")
    void createResponse_emptyPolicies_valid() {
        // When
        ConsentPolicyResponse response = new ConsentPolicyResponse(
                List.of()
        );

        // Then
        assertThat(response.policies()).isEmpty();
    }

    @Test
    @DisplayName("should create response with null policies")
    void createResponse_nullPolicies_valid() {
        // When
        ConsentPolicyResponse response = new ConsentPolicyResponse(
                null
        );

        // Then
        assertThat(response.policies()).isNull();
    }

    @Test
    @DisplayName("should test nested record PolicyInfo")
    void nestedRecord_PolicyInfo_valid() {
        // Given
        LocalDate effectiveDate = LocalDate.now();
        
        ConsentPolicyResponse.PolicyInfo policy = new ConsentPolicyResponse.PolicyInfo(
                "policy1",
                ConsentType.PRIVACY_POLICY,
                "1.0",
                effectiveDate,
                "abc123",
                "https://example.com/policy",
                "en-GB",
                true
        );

        // Then
        assertThat(policy.policyId()).isEqualTo("policy1");
        assertThat(policy.policyType()).isEqualTo(ConsentType.PRIVACY_POLICY);
        assertThat(policy.version()).isEqualTo("1.0");
        assertThat(policy.effectiveDate()).isEqualTo(effectiveDate);
        assertThat(policy.contentHash()).isEqualTo("abc123");
        assertThat(policy.policyUrl()).isEqualTo("https://example.com/policy");
        assertThat(policy.locale()).isEqualTo("en-GB");
        assertThat(policy.isActive()).isTrue();
    }

    @Test
    @DisplayName("should test nested record with isActive false")
    void nestedRecord_isActiveFalse_valid() {
        // Given
        ConsentPolicyResponse.PolicyInfo policy = new ConsentPolicyResponse.PolicyInfo(
                "policy1",
                ConsentType.PRIVACY_POLICY,
                "1.0",
                LocalDate.now(),
                "abc123",
                "https://example.com/policy",
                "en-GB",
                false
        );

        // Then
        assertThat(policy.isActive()).isFalse();
    }

    @Test
    @DisplayName("should test nested record equality")
    void nestedRecord_equality_valid() {
        // Given
        LocalDate effectiveDate = LocalDate.now();
        
        ConsentPolicyResponse.PolicyInfo policy1 = new ConsentPolicyResponse.PolicyInfo(
                "policy1",
                ConsentType.PRIVACY_POLICY,
                "1.0",
                effectiveDate,
                "abc123",
                "https://example.com/policy",
                "en-GB",
                true
        );
        
        ConsentPolicyResponse.PolicyInfo policy2 = new ConsentPolicyResponse.PolicyInfo(
                "policy1",
                ConsentType.PRIVACY_POLICY,
                "1.0",
                effectiveDate,
                "abc123",
                "https://example.com/policy",
                "en-GB",
                true
        );

        // Then
        assertThat(policy1).isEqualTo(policy2);
        assertThat(policy1.hashCode()).isEqualTo(policy2.hashCode());
    }

    @Test
    @DisplayName("should handle all consent types in nested record")
    void nestedRecord_allConsentTypes_valid() {
        LocalDate effectiveDate = LocalDate.now();
        
        for (ConsentType type : ConsentType.values()) {
            ConsentPolicyResponse.PolicyInfo policy = new ConsentPolicyResponse.PolicyInfo(
                    "policy1",
                    type,
                    "1.0",
                    effectiveDate,
                    "abc123",
                    "https://example.com/policy",
                    "en-GB",
                    true
            );
            
            assertThat(policy.policyType()).isEqualTo(type);
        }
    }

    @Test
    @DisplayName("should test record equality")
    void recordEquality_sameValues_equal() {
        // Given
        ConsentPolicyResponse.PolicyInfo policy = new ConsentPolicyResponse.PolicyInfo(
                "policy1",
                ConsentType.PRIVACY_POLICY,
                "1.0",
                LocalDate.now(),
                "abc123",
                "https://example.com/policy",
                "en-GB",
                true
        );

        ConsentPolicyResponse response1 = new ConsentPolicyResponse(
                List.of(policy)
        );

        ConsentPolicyResponse response2 = new ConsentPolicyResponse(
                List.of(policy)
        );

        // Then
        assertThat(response1).isEqualTo(response2);
        assertThat(response1.hashCode()).isEqualTo(response2.hashCode());
    }

    @Test
    @DisplayName("should test record toString")
    void recordToString_containsFields() {
        // Given
        ConsentPolicyResponse.PolicyInfo policy = new ConsentPolicyResponse.PolicyInfo(
                "policy1",
                ConsentType.PRIVACY_POLICY,
                "1.0",
                LocalDate.now(),
                "abc123",
                "https://example.com/policy",
                "en-GB",
                true
        );

        ConsentPolicyResponse response = new ConsentPolicyResponse(
                List.of(policy)
        );

        // When
        String toString = response.toString();

        // Then
        assertThat(toString).contains("ConsentPolicyResponse");
    }
}

