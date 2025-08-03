package uk.gegc.kidsgptbackend.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gegc.kidsgptbackend.dto.consent.ConsentStatusResponse;
import uk.gegc.kidsgptbackend.model.consent.ConsentStatus;
import uk.gegc.kidsgptbackend.model.consent.ConsentType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ConsentStatusResponseSerializationTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Test
    @DisplayName("4.1 Enums serialize as strings - Given ConsentStatusByType.type/status populated, Then JSON shows DATA_PROCESSING, GRANTED etc.")
    void enumsSerializeAsStrings_givenConsentStatusByTypeTypeStatusPopulated_thenJsonShowsDataProcessingGrantedEtc() throws Exception {
        // Given: ConsentStatusByType.type/status populated with enum values
        UUID consentId = UUID.randomUUID();
        LocalDateTime timestamp = LocalDateTime.of(2024, 1, 15, 10, 30, 0);
        
        ConsentStatusResponse.ConsentStatusByType privacyPolicyStatus = new ConsentStatusResponse.ConsentStatusByType(
                ConsentType.PRIVACY_POLICY,
                "1.0.0",
                ConsentStatus.GRANTED,
                timestamp,
                "https://example.com/privacy"
        );
        
        ConsentStatusResponse.ConsentStatusByType termsOfServiceStatus = new ConsentStatusResponse.ConsentStatusByType(
                ConsentType.TERMS_OF_SERVICE,
                "2.0.0",
                ConsentStatus.WITHDRAWN,
                timestamp,
                "https://example.com/terms"
        );
        
        ConsentStatusResponse.ConsentStatusByType parentalConsentStatus = new ConsentStatusResponse.ConsentStatusByType(
                ConsentType.PARENTAL_CONSENT,
                "1.5.0",
                ConsentStatus.EXPIRED,
                timestamp,
                "https://example.com/parental"
        );
        
        ConsentStatusResponse.ConsentStatusByType dataProcessingStatus = new ConsentStatusResponse.ConsentStatusByType(
                ConsentType.DATA_PROCESSING,
                "3.0.0",
                ConsentStatus.GRANTED,
                timestamp,
                "https://example.com/data"
        );

        ConsentStatusResponse response = new ConsentStatusResponse(
                List.of(privacyPolicyStatus, termsOfServiceStatus, parentalConsentStatus, dataProcessingStatus),
                false,
                consentId
        );

        // When: Serialize to JSON
        String json = objectMapper.writeValueAsString(response);

        // Then: JSON shows enum values as strings
        assertThat(json).contains("\"type\":\"PRIVACY_POLICY\"");
        assertThat(json).contains("\"type\":\"TERMS_OF_SERVICE\"");
        assertThat(json).contains("\"type\":\"PARENTAL_CONSENT\"");
        assertThat(json).contains("\"type\":\"DATA_PROCESSING\"");
        
        assertThat(json).contains("\"status\":\"GRANTED\"");
        assertThat(json).contains("\"status\":\"WITHDRAWN\"");
        assertThat(json).contains("\"status\":\"EXPIRED\"");

        // Verify the complete structure is correct
        assertThat(json).contains("\"latestByType\":[");
        assertThat(json).contains("\"reconsentNeeded\":false");
        assertThat(json).contains("\"consentId\":\"" + consentId + "\"");
        
        // Verify each ConsentStatusByType has the correct structure
        assertThat(json).contains("\"version\":\"1.0.0\"");
        assertThat(json).contains("\"version\":\"2.0.0\"");
        assertThat(json).contains("\"version\":\"1.5.0\"");
        assertThat(json).contains("\"version\":\"3.0.0\"");
        
        assertThat(json).contains("\"policyUrl\":\"https://example.com/privacy\"");
        assertThat(json).contains("\"policyUrl\":\"https://example.com/terms\"");
        assertThat(json).contains("\"policyUrl\":\"https://example.com/parental\"");
        assertThat(json).contains("\"policyUrl\":\"https://example.com/data\"");
    }

    @Test
    @DisplayName("4.1 Enums serialize as strings - Single ConsentStatusByType with all enum values")
    void enumsSerializeAsStrings_singleConsentStatusByTypeWithAllEnumValues() throws Exception {
        // Given: Single ConsentStatusByType with all enum values
        ConsentStatusResponse.ConsentStatusByType status = new ConsentStatusResponse.ConsentStatusByType(
                ConsentType.DATA_PROCESSING,
                "1.0.0",
                ConsentStatus.GRANTED,
                LocalDateTime.of(2024, 1, 15, 10, 30, 0),
                "https://example.com/policy"
        );

        // When: Serialize to JSON
        String json = objectMapper.writeValueAsString(status);

        // Then: JSON shows enum values as strings exactly as specified
        assertThat(json).contains("\"type\":\"DATA_PROCESSING\"");
        assertThat(json).contains("\"status\":\"GRANTED\"");
        
        // Verify it's not serialized as numbers or other formats
        assertThat(json).doesNotContain("\"type\":0");
        assertThat(json).doesNotContain("\"type\":1");
        assertThat(json).doesNotContain("\"status\":0");
        assertThat(json).doesNotContain("\"status\":1");
    }

    @Test
    @DisplayName("4.1 Enums serialize as strings - All ConsentType enum values")
    void enumsSerializeAsStrings_allConsentTypeEnumValues() throws Exception {
        // Given: Test all ConsentType enum values
        LocalDateTime timestamp = LocalDateTime.of(2024, 1, 15, 10, 30, 0);
        
        ConsentStatusResponse.ConsentStatusByType privacyPolicy = new ConsentStatusResponse.ConsentStatusByType(
                ConsentType.PRIVACY_POLICY,
                "1.0.0",
                ConsentStatus.GRANTED,
                timestamp,
                "https://example.com/privacy"
        );
        
        ConsentStatusResponse.ConsentStatusByType termsOfService = new ConsentStatusResponse.ConsentStatusByType(
                ConsentType.TERMS_OF_SERVICE,
                "1.0.0",
                ConsentStatus.GRANTED,
                timestamp,
                "https://example.com/terms"
        );
        
        ConsentStatusResponse.ConsentStatusByType parentalConsent = new ConsentStatusResponse.ConsentStatusByType(
                ConsentType.PARENTAL_CONSENT,
                "1.0.0",
                ConsentStatus.GRANTED,
                timestamp,
                "https://example.com/parental"
        );
        
        ConsentStatusResponse.ConsentStatusByType dataProcessing = new ConsentStatusResponse.ConsentStatusByType(
                ConsentType.DATA_PROCESSING,
                "1.0.0",
                ConsentStatus.GRANTED,
                timestamp,
                "https://example.com/data"
        );

        // When: Serialize each to JSON
        String privacyJson = objectMapper.writeValueAsString(privacyPolicy);
        String termsJson = objectMapper.writeValueAsString(termsOfService);
        String parentalJson = objectMapper.writeValueAsString(parentalConsent);
        String dataJson = objectMapper.writeValueAsString(dataProcessing);

        // Then: All enum values serialize as strings
        assertThat(privacyJson).contains("\"type\":\"PRIVACY_POLICY\"");
        assertThat(termsJson).contains("\"type\":\"TERMS_OF_SERVICE\"");
        assertThat(parentalJson).contains("\"type\":\"PARENTAL_CONSENT\"");
        assertThat(dataJson).contains("\"type\":\"DATA_PROCESSING\"");
    }

    @Test
    @DisplayName("4.1 Enums serialize as strings - All ConsentStatus enum values")
    void enumsSerializeAsStrings_allConsentStatusEnumValues() throws Exception {
        // Given: Test all ConsentStatus enum values
        LocalDateTime timestamp = LocalDateTime.of(2024, 1, 15, 10, 30, 0);
        
        ConsentStatusResponse.ConsentStatusByType granted = new ConsentStatusResponse.ConsentStatusByType(
                ConsentType.PRIVACY_POLICY,
                "1.0.0",
                ConsentStatus.GRANTED,
                timestamp,
                "https://example.com/policy"
        );
        
        ConsentStatusResponse.ConsentStatusByType withdrawn = new ConsentStatusResponse.ConsentStatusByType(
                ConsentType.PRIVACY_POLICY,
                "1.0.0",
                ConsentStatus.WITHDRAWN,
                timestamp,
                "https://example.com/policy"
        );
        
        ConsentStatusResponse.ConsentStatusByType expired = new ConsentStatusResponse.ConsentStatusByType(
                ConsentType.PRIVACY_POLICY,
                "1.0.0",
                ConsentStatus.EXPIRED,
                timestamp,
                "https://example.com/policy"
        );

        // When: Serialize each to JSON
        String grantedJson = objectMapper.writeValueAsString(granted);
        String withdrawnJson = objectMapper.writeValueAsString(withdrawn);
        String expiredJson = objectMapper.writeValueAsString(expired);

        // Then: All enum values serialize as strings
        assertThat(grantedJson).contains("\"status\":\"GRANTED\"");
        assertThat(withdrawnJson).contains("\"status\":\"WITHDRAWN\"");
        assertThat(expiredJson).contains("\"status\":\"EXPIRED\"");
    }

    @Test
    @DisplayName("4.2 Nulls and empty collections - consentId may be null")
    void nullsAndEmptyCollections_consentIdMayBeNull() throws Exception {
        // Given: ConsentStatusResponse with null consentId
        ConsentStatusResponse response = new ConsentStatusResponse(
                List.of(), // empty latestByType
                false,
                null // consentId = null
        );

        // When: Serialize to JSON
        String json = objectMapper.writeValueAsString(response);

        // Then: JSON shows consentId as null
        assertThat(json).contains("\"consentId\":null");
        assertThat(json).contains("\"latestByType\":[]");
        assertThat(json).contains("\"reconsentNeeded\":false");
        
        // Verify the complete structure is correct
        assertThat(json).contains("\"latestByType\":[");
        assertThat(json).doesNotContain("\"consentId\":\"");
    }

    @Test
    @DisplayName("4.2 Nulls and empty collections - latestByType must be present as array (possibly empty)")
    void nullsAndEmptyCollections_latestByTypeMustBePresentAsArrayPossiblyEmpty() throws Exception {
        // Given: ConsentStatusResponse with empty latestByType
        UUID consentId = UUID.randomUUID();
        ConsentStatusResponse response = new ConsentStatusResponse(
                List.of(), // empty latestByType
                true,
                consentId
        );

        // When: Serialize to JSON
        String json = objectMapper.writeValueAsString(response);

        // Then: latestByType must be present as an array (possibly empty)
        assertThat(json).contains("\"latestByType\":[]");
        assertThat(json).contains("\"consentId\":\"" + consentId + "\"");
        assertThat(json).contains("\"reconsentNeeded\":true");
        
        // Verify it's an empty array, not null or missing
        assertThat(json).doesNotContain("\"latestByType\":null");
        assertThat(json).doesNotContain("\"latestByType\":\"");
    }

    @Test
    @DisplayName("4.2 Nulls and empty collections - latestByType with populated array")
    void nullsAndEmptyCollections_latestByTypeWithPopulatedArray() throws Exception {
        // Given: ConsentStatusResponse with populated latestByType
        UUID consentId = UUID.randomUUID();
        LocalDateTime timestamp = LocalDateTime.of(2024, 1, 15, 10, 30, 0);
        
        ConsentStatusResponse.ConsentStatusByType status = new ConsentStatusResponse.ConsentStatusByType(
                ConsentType.PRIVACY_POLICY,
                "1.0.0",
                ConsentStatus.GRANTED,
                timestamp,
                "https://example.com/policy"
        );

        ConsentStatusResponse response = new ConsentStatusResponse(
                List.of(status), // populated latestByType
                false,
                consentId
        );

        // When: Serialize to JSON
        String json = objectMapper.writeValueAsString(response);

        // Then: latestByType must be present as an array with content
        assertThat(json).contains("\"latestByType\":[");
        assertThat(json).contains("\"type\":\"PRIVACY_POLICY\"");
        assertThat(json).contains("\"status\":\"GRANTED\"");
        assertThat(json).contains("\"consentId\":\"" + consentId + "\"");
        assertThat(json).contains("\"reconsentNeeded\":false");
        
        // Verify it's an array with content, not empty
        assertThat(json).doesNotContain("\"latestByType\":[]");
        assertThat(json).doesNotContain("\"latestByType\":null");
    }

    @Test
    @DisplayName("4.2 Nulls and empty collections - both null consentId and empty latestByType")
    void nullsAndEmptyCollections_bothNullConsentIdAndEmptyLatestByType() throws Exception {
        // Given: ConsentStatusResponse with both null consentId and empty latestByType
        ConsentStatusResponse response = new ConsentStatusResponse(
                List.of(), // empty latestByType
                true,
                null // consentId = null
        );

        // When: Serialize to JSON
        String json = objectMapper.writeValueAsString(response);

        // Then: Both conditions are met
        assertThat(json).contains("\"consentId\":null");
        assertThat(json).contains("\"latestByType\":[]");
        assertThat(json).contains("\"reconsentNeeded\":true");
        
        // Verify the complete structure is correct
        assertThat(json).contains("\"latestByType\":[");
        assertThat(json).doesNotContain("\"consentId\":\"");
        assertThat(json).doesNotContain("\"latestByType\":null");
    }

    @Test
    @DisplayName("4.3 Timestamp format - timestamp serialized as ISO-8601 without timezone suffix (per LocalDateTime)")
    void timestampFormat_timestampSerializedAsIso8601WithoutTimezoneSuffix() throws Exception {
        // Given: ConsentStatusByType with specific LocalDateTime timestamp
        LocalDateTime timestamp = LocalDateTime.of(2024, 1, 15, 10, 30, 45, 123456789);
        
        ConsentStatusResponse.ConsentStatusByType status = new ConsentStatusResponse.ConsentStatusByType(
                ConsentType.PRIVACY_POLICY,
                "1.0.0",
                ConsentStatus.GRANTED,
                timestamp,
                "https://example.com/policy"
        );

        // When: Serialize to JSON
        String json = objectMapper.writeValueAsString(status);

        // Then: timestamp is serialized as ISO-8601 without timezone suffix
        assertThat(json).contains("\"timestamp\":\"2024-01-15T10:30:45.123456789\"");
        
        // Verify it's not serialized with timezone suffix
        assertThat(json).doesNotContain("\"timestamp\":\"2024-01-15T10:30:45.123456789Z\"");
        assertThat(json).doesNotContain("\"timestamp\":\"2024-01-15T10:30:45.123456789+00:00\"");
        assertThat(json).doesNotContain("\"timestamp\":\"2024-01-15T10:30:45.123456789-00:00\"");
        
        // Verify it's not serialized as timestamp numbers
        assertThat(json).doesNotContain("\"timestamp\":1705315845");
        assertThat(json).doesNotContain("\"timestamp\":1705315845123");
    }

    @Test
    @DisplayName("4.3 Timestamp format - different LocalDateTime values serialize correctly")
    void timestampFormat_differentLocalDateTimeValuesSerializeCorrectly() throws Exception {
        // Given: Different LocalDateTime values
        LocalDateTime midnight = LocalDateTime.of(2024, 1, 1, 0, 0, 0);
        LocalDateTime noon = LocalDateTime.of(2024, 6, 15, 12, 0, 0);
        LocalDateTime withNanos = LocalDateTime.of(2024, 12, 31, 23, 59, 59, 999999999);
        
        ConsentStatusResponse.ConsentStatusByType midnightStatus = new ConsentStatusResponse.ConsentStatusByType(
                ConsentType.PRIVACY_POLICY,
                "1.0.0",
                ConsentStatus.GRANTED,
                midnight,
                "https://example.com/policy"
        );
        
        ConsentStatusResponse.ConsentStatusByType noonStatus = new ConsentStatusResponse.ConsentStatusByType(
                ConsentType.TERMS_OF_SERVICE,
                "1.0.0",
                ConsentStatus.GRANTED,
                noon,
                "https://example.com/terms"
        );
        
        ConsentStatusResponse.ConsentStatusByType nanosStatus = new ConsentStatusResponse.ConsentStatusByType(
                ConsentType.DATA_PROCESSING,
                "1.0.0",
                ConsentStatus.GRANTED,
                withNanos,
                "https://example.com/data"
        );

        // When: Serialize each to JSON
        String midnightJson = objectMapper.writeValueAsString(midnightStatus);
        String noonJson = objectMapper.writeValueAsString(noonStatus);
        String nanosJson = objectMapper.writeValueAsString(nanosStatus);

        // Then: All timestamps are serialized as ISO-8601 without timezone suffix
        assertThat(midnightJson).contains("\"timestamp\":\"2024-01-01T00:00:00\"");
        assertThat(noonJson).contains("\"timestamp\":\"2024-06-15T12:00:00\"");
        assertThat(nanosJson).contains("\"timestamp\":\"2024-12-31T23:59:59.999999999\"");
        
        // Verify none have timezone suffixes
        assertThat(midnightJson).doesNotContain("Z\"");
        assertThat(noonJson).doesNotContain("Z\"");
        assertThat(nanosJson).doesNotContain("Z\"");
        assertThat(midnightJson).doesNotContain("+");
        assertThat(noonJson).doesNotContain("+");
        assertThat(nanosJson).doesNotContain("+");
    }

    @Test
    @DisplayName("4.3 Timestamp format - timestamp in ConsentStatusResponse structure")
    void timestampFormat_timestampInConsentStatusResponseStructure() throws Exception {
        // Given: ConsentStatusResponse with timestamp
        UUID consentId = UUID.randomUUID();
        LocalDateTime timestamp = LocalDateTime.of(2024, 1, 15, 10, 30, 0);
        
        ConsentStatusResponse.ConsentStatusByType status = new ConsentStatusResponse.ConsentStatusByType(
                ConsentType.PRIVACY_POLICY,
                "1.0.0",
                ConsentStatus.GRANTED,
                timestamp,
                "https://example.com/policy"
        );

        ConsentStatusResponse response = new ConsentStatusResponse(
                List.of(status),
                false,
                consentId
        );

        // When: Serialize to JSON
        String json = objectMapper.writeValueAsString(response);

        // Then: timestamp is serialized as ISO-8601 without timezone suffix within the structure
        assertThat(json).contains("\"timestamp\":\"2024-01-15T10:30:00\"");
        assertThat(json).contains("\"type\":\"PRIVACY_POLICY\"");
        assertThat(json).contains("\"status\":\"GRANTED\"");
        assertThat(json).contains("\"consentId\":\"" + consentId + "\"");
        
        // Verify the complete structure is correct
        assertThat(json).contains("\"latestByType\":[");
        assertThat(json).contains("\"reconsentNeeded\":false");
        
        // Verify timestamp format is correct (no timezone suffix)
        assertThat(json).doesNotContain("\"timestamp\":\"2024-01-15T10:30:00Z\"");
        assertThat(json).doesNotContain("\"timestamp\":\"2024-01-15T10:30:00+00:00\"");
    }
} 