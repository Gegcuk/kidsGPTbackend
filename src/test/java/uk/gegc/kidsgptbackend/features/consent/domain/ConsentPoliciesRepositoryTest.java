package uk.gegc.kidsgptbackend.features.consent.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import uk.gegc.kidsgptbackend.features.consent.domain.model.*;
import uk.gegc.kidsgptbackend.features.consent.domain.repository.ConsentLedgerRepository;
import uk.gegc.kidsgptbackend.features.consent.domain.repository.ConsentPoliciesRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class ConsentPoliciesRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ConsentPoliciesRepository consentPoliciesRepository;

    private ConsentType testPolicyType;

    @BeforeEach
    void setUp() {
        testPolicyType = ConsentType.PRIVACY_POLICY;
    }

    @Test
    void findActivePoliciesByTypeAndDate_GivenPoliciesWithEffectiveDateBeforeAfterTodayAndIsActiveTrueFalse_ReturnsOnlyActiveWithEffectiveDateLessThanOrEqualToToday() {
        // Given: policies with effectiveDate before/after today and isActive=true/false
        LocalDate today = LocalDate.of(2024, 1, 15);
        LocalDate beforeToday = LocalDate.of(2024, 1, 10);
        LocalDate afterToday = LocalDate.of(2024, 1, 20);

        // Create policies with different combinations of effectiveDate and isActive
        ConsentPolicies activeBeforeToday1 = ConsentPolicies.builder()
                .policyType(testPolicyType)
                .version("1.0.0")
                .effectiveDate(beforeToday)
                .contentHash("hash1")
                .policyUrl("https://example.com/policy1")
                .locale("en-GB")
                .isActive(true)
                .build();

        ConsentPolicies activeBeforeToday2 = ConsentPolicies.builder()
                .policyType(testPolicyType)
                .version("1.1.0")
                .effectiveDate(beforeToday.minusDays(2)) // Earlier than beforeToday
                .contentHash("hash2")
                .policyUrl("https://example.com/policy2")
                .locale("en-GB")
                .isActive(true)
                .build();

        ConsentPolicies activeOnToday = ConsentPolicies.builder()
                .policyType(testPolicyType)
                .version("2.0.0")
                .effectiveDate(today)
                .contentHash("hash3")
                .policyUrl("https://example.com/policy3")
                .locale("en-GB")
                .isActive(true)
                .build();

        ConsentPolicies inactiveBeforeToday = ConsentPolicies.builder()
                .policyType(testPolicyType)
                .version("0.9.0")
                .effectiveDate(beforeToday)
                .contentHash("hash4")
                .policyUrl("https://example.com/policy4")
                .locale("en-GB")
                .isActive(false)
                .build();

        ConsentPolicies activeAfterToday = ConsentPolicies.builder()
                .policyType(testPolicyType)
                .version("3.0.0")
                .effectiveDate(afterToday)
                .contentHash("hash5")
                .policyUrl("https://example.com/policy5")
                .locale("en-GB")
                .isActive(true)
                .build();

        ConsentPolicies inactiveAfterToday = ConsentPolicies.builder()
                .policyType(testPolicyType)
                .version("2.5.0")
                .effectiveDate(afterToday)
                .contentHash("hash6")
                .policyUrl("https://example.com/policy6")
                .locale("en-GB")
                .isActive(false)
                .build();

        // Persist all policies
        entityManager.persistAndFlush(activeBeforeToday1);
        entityManager.persistAndFlush(activeBeforeToday2);
        entityManager.persistAndFlush(activeOnToday);
        entityManager.persistAndFlush(inactiveBeforeToday);
        entityManager.persistAndFlush(activeAfterToday);
        entityManager.persistAndFlush(inactiveAfterToday);
        entityManager.clear();

        // When: Call the repository method
        List<ConsentPolicies> result = consentPoliciesRepository.findActivePoliciesByTypeAndDate(testPolicyType, today);

        // Then: only active with effectiveDate <= today returned, ordered by effectiveDate DESC
        assertEquals(3, result.size(), "Should return exactly 3 active policies with effectiveDate <= today");

        // Verify order: effectiveDate DESC
        assertEquals("2.0.0", result.get(0).getVersion(), 
                "First policy should be the one with latest effectiveDate (today)");
        assertEquals("1.0.0", result.get(1).getVersion(), 
                "Second policy should be the one with second latest effectiveDate");
        assertEquals("1.1.0", result.get(2).getVersion(), 
                "Third policy should be the one with earliest effectiveDate");

        // Verify all returned policies are active
        result.forEach(policy -> {
            assertTrue(policy.getIsActive(), "All returned policies should be active");
            assertTrue(policy.getEffectiveDate().compareTo(today) <= 0, 
                    "All returned policies should have effectiveDate <= today");
            assertEquals(testPolicyType, policy.getPolicyType(), 
                    "All returned policies should be of the correct type");
        });

        // Verify effectiveDate values are in descending order
        assertTrue(result.get(0).getEffectiveDate().compareTo(result.get(1).getEffectiveDate()) >= 0, 
                "First policy should have later or equal effectiveDate than second");
        assertTrue(result.get(1).getEffectiveDate().compareTo(result.get(2).getEffectiveDate()) >= 0, 
                "Second policy should have later or equal effectiveDate than third");
    }

    @Test
    void findActivePoliciesByTypeAndDate_NoActivePoliciesBeforeToday_ReturnsEmptyList() {
        // Given: only inactive policies or policies with effectiveDate after today
        LocalDate today = LocalDate.of(2024, 1, 15);
        LocalDate afterToday = LocalDate.of(2024, 1, 20);

        ConsentPolicies inactiveBeforeToday = ConsentPolicies.builder()
                .policyType(testPolicyType)
                .version("1.0.0")
                .effectiveDate(LocalDate.of(2024, 1, 10))
                .contentHash("hash1")
                .policyUrl("https://example.com/policy1")
                .locale("en-GB")
                .isActive(false)
                .build();

        ConsentPolicies activeAfterToday = ConsentPolicies.builder()
                .policyType(testPolicyType)
                .version("2.0.0")
                .effectiveDate(afterToday)
                .contentHash("hash2")
                .policyUrl("https://example.com/policy2")
                .locale("en-GB")
                .isActive(true)
                .build();

        // Persist policies
        entityManager.persistAndFlush(inactiveBeforeToday);
        entityManager.persistAndFlush(activeAfterToday);
        entityManager.clear();

        // When: Call the repository method
        List<ConsentPolicies> result = consentPoliciesRepository.findActivePoliciesByTypeAndDate(testPolicyType, today);

        // Then: should return empty list
        assertTrue(result.isEmpty(), "Should return empty list when no active policies with effectiveDate <= today");
    }

    @Test
    void findActivePoliciesByTypeAndDate_DifferentPolicyTypes_OnlyReturnsMatchingType() {
        // Given: policies of different types
        LocalDate today = LocalDate.of(2024, 1, 15);
        LocalDate beforeToday = LocalDate.of(2024, 1, 10);

        ConsentPolicies privacyPolicy = ConsentPolicies.builder()
                .policyType(ConsentType.PRIVACY_POLICY)
                .version("1.0.0")
                .effectiveDate(beforeToday)
                .contentHash("hash1")
                .policyUrl("https://example.com/privacy")
                .locale("en-GB")
                .isActive(true)
                .build();

        ConsentPolicies termsPolicy = ConsentPolicies.builder()
                .policyType(ConsentType.TERMS_OF_SERVICE)
                .version("1.0.0")
                .effectiveDate(beforeToday)
                .contentHash("hash2")
                .policyUrl("https://example.com/terms")
                .locale("en-GB")
                .isActive(true)
                .build();

        ConsentPolicies dataProcessingPolicy = ConsentPolicies.builder()
                .policyType(ConsentType.DATA_PROCESSING)
                .version("1.0.0")
                .effectiveDate(beforeToday)
                .contentHash("hash3")
                .policyUrl("https://example.com/data")
                .locale("en-GB")
                .isActive(true)
                .build();

        // Persist policies
        entityManager.persistAndFlush(privacyPolicy);
        entityManager.persistAndFlush(termsPolicy);
        entityManager.persistAndFlush(dataProcessingPolicy);
        entityManager.clear();

        // When: Call the repository method for PRIVACY_POLICY
        List<ConsentPolicies> result = consentPoliciesRepository.findActivePoliciesByTypeAndDate(ConsentType.PRIVACY_POLICY, today);

        // Then: should return only PRIVACY_POLICY policies
        assertEquals(1, result.size(), "Should return only one policy");
        assertEquals(ConsentType.PRIVACY_POLICY, result.get(0).getPolicyType(), 
                "Should return only PRIVACY_POLICY type");
        assertEquals("1.0.0", result.get(0).getVersion(), 
                "Should return the correct version");

        // When: Call the repository method for TERMS_OF_SERVICE
        List<ConsentPolicies> termsResult = consentPoliciesRepository.findActivePoliciesByTypeAndDate(ConsentType.TERMS_OF_SERVICE, today);

        // Then: should return only TERMS_OF_SERVICE policies
        assertEquals(1, termsResult.size(), "Should return only one policy");
        assertEquals(ConsentType.TERMS_OF_SERVICE, termsResult.get(0).getPolicyType(), 
                "Should return only TERMS_OF_SERVICE type");
    }

    @Test
    void findActivePoliciesByTypeAndDate_SameEffectiveDate_ReturnsAllInCorrectOrder() {
        // Given: multiple policies with the same effectiveDate
        LocalDate today = LocalDate.of(2024, 1, 15);
        LocalDate sameDate = LocalDate.of(2024, 1, 10);

        ConsentPolicies policy1 = ConsentPolicies.builder()
                .policyType(testPolicyType)
                .version("1.0.0")
                .effectiveDate(sameDate)
                .contentHash("hash1")
                .policyUrl("https://example.com/policy1")
                .locale("en-GB")
                .isActive(true)
                .build();

        ConsentPolicies policy2 = ConsentPolicies.builder()
                .policyType(testPolicyType)
                .version("1.1.0")
                .effectiveDate(sameDate)
                .contentHash("hash2")
                .policyUrl("https://example.com/policy2")
                .locale("en-GB")
                .isActive(true)
                .build();

        ConsentPolicies policy3 = ConsentPolicies.builder()
                .policyType(testPolicyType)
                .version("1.2.0")
                .effectiveDate(sameDate)
                .contentHash("hash3")
                .policyUrl("https://example.com/policy3")
                .locale("en-GB")
                .isActive(true)
                .build();

        // Persist policies in non-chronological order
        entityManager.persistAndFlush(policy2);
        entityManager.persistAndFlush(policy1);
        entityManager.persistAndFlush(policy3);
        entityManager.clear();

        // When: Call the repository method
        List<ConsentPolicies> result = consentPoliciesRepository.findActivePoliciesByTypeAndDate(testPolicyType, today);

        // Then: should return all 3 policies with same effectiveDate
        assertEquals(3, result.size(), "Should return all 3 policies with same effectiveDate");

        // Verify all have the same effectiveDate
        result.forEach(policy -> {
            assertEquals(sameDate, policy.getEffectiveDate(), 
                    "All policies should have the same effectiveDate");
            assertTrue(policy.getIsActive(), "All policies should be active");
        });
    }

    @Test
    void findActivePoliciesByTypeLocaleAndDate_GivenPoliciesWithEffectiveDateBeforeAfterTodayAndIsActiveTrueFalse_ReturnsOnlyActiveWithEffectiveDateLessThanOrEqualToTodayAndMatchingLocale() {
        // Given: policies with effectiveDate before/after today and isActive=true/false
        LocalDate today = LocalDate.of(2024, 1, 15);
        LocalDate beforeToday = LocalDate.of(2024, 1, 10);
        LocalDate afterToday = LocalDate.of(2024, 1, 20);
        String targetLocale = "en-GB";
        String otherLocale = "fr-FR";

        // Create policies with different combinations of effectiveDate, isActive, and locale
        ConsentPolicies activeBeforeTodayMatchingLocale1 = ConsentPolicies.builder()
                .policyType(testPolicyType)
                .version("1.0.0")
                .effectiveDate(beforeToday)
                .contentHash("hash1")
                .policyUrl("https://example.com/policy1")
                .locale(targetLocale)
                .isActive(true)
                .build();

        ConsentPolicies activeBeforeTodayMatchingLocale2 = ConsentPolicies.builder()
                .policyType(testPolicyType)
                .version("1.1.0")
                .effectiveDate(beforeToday.minusDays(2)) // Earlier than beforeToday
                .contentHash("hash2")
                .policyUrl("https://example.com/policy2")
                .locale(targetLocale)
                .isActive(true)
                .build();

        ConsentPolicies activeOnTodayMatchingLocale = ConsentPolicies.builder()
                .policyType(testPolicyType)
                .version("2.0.0")
                .effectiveDate(today)
                .contentHash("hash3")
                .policyUrl("https://example.com/policy3")
                .locale(targetLocale)
                .isActive(true)
                .build();

        ConsentPolicies inactiveBeforeTodayMatchingLocale = ConsentPolicies.builder()
                .policyType(testPolicyType)
                .version("0.9.0")
                .effectiveDate(beforeToday)
                .contentHash("hash4")
                .policyUrl("https://example.com/policy4")
                .locale(targetLocale)
                .isActive(false)
                .build();

        ConsentPolicies activeAfterTodayMatchingLocale = ConsentPolicies.builder()
                .policyType(testPolicyType)
                .version("3.0.0")
                .effectiveDate(afterToday)
                .contentHash("hash5")
                .policyUrl("https://example.com/policy5")
                .locale(targetLocale)
                .isActive(true)
                .build();

        ConsentPolicies activeBeforeTodayOtherLocale = ConsentPolicies.builder()
                .policyType(testPolicyType)
                .version("1.5.0")
                .effectiveDate(beforeToday)
                .contentHash("hash6")
                .policyUrl("https://example.com/policy6")
                .locale(otherLocale)
                .isActive(true)
                .build();

        ConsentPolicies activeOnTodayOtherLocale = ConsentPolicies.builder()
                .policyType(testPolicyType)
                .version("2.5.0")
                .effectiveDate(today)
                .contentHash("hash7")
                .policyUrl("https://example.com/policy7")
                .locale(otherLocale)
                .isActive(true)
                .build();

        // Persist all policies
        entityManager.persistAndFlush(activeBeforeTodayMatchingLocale1);
        entityManager.persistAndFlush(activeBeforeTodayMatchingLocale2);
        entityManager.persistAndFlush(activeOnTodayMatchingLocale);
        entityManager.persistAndFlush(inactiveBeforeTodayMatchingLocale);
        entityManager.persistAndFlush(activeAfterTodayMatchingLocale);
        entityManager.persistAndFlush(activeBeforeTodayOtherLocale);
        entityManager.persistAndFlush(activeOnTodayOtherLocale);
        entityManager.clear();

        // When: Call the repository method with target locale
        List<ConsentPolicies> result = consentPoliciesRepository.findActivePoliciesByTypeLocaleAndDate(testPolicyType, targetLocale, today);

        // Then: only active with effectiveDate <= today and matching locale returned, ordered by effectiveDate DESC
        assertEquals(3, result.size(), "Should return exactly 3 active policies with effectiveDate <= today and matching locale");

        // Verify order: effectiveDate DESC
        assertEquals("2.0.0", result.get(0).getVersion(), 
                "First policy should be the one with latest effectiveDate (today)");
        assertEquals("1.0.0", result.get(1).getVersion(), 
                "Second policy should be the one with second latest effectiveDate");
        assertEquals("1.1.0", result.get(2).getVersion(), 
                "Third policy should be the one with earliest effectiveDate");

        // Verify all returned policies are active, have correct locale, and effectiveDate <= today
        result.forEach(policy -> {
            assertTrue(policy.getIsActive(), "All returned policies should be active");
            assertTrue(policy.getEffectiveDate().compareTo(today) <= 0, 
                    "All returned policies should have effectiveDate <= today");
            assertEquals(testPolicyType, policy.getPolicyType(), 
                    "All returned policies should be of the correct type");
            assertEquals(targetLocale, policy.getLocale(), 
                    "All returned policies should have the matching locale");
        });

        // Verify effectiveDate values are in descending order
        assertTrue(result.get(0).getEffectiveDate().compareTo(result.get(1).getEffectiveDate()) >= 0, 
                "First policy should have later or equal effectiveDate than second");
        assertTrue(result.get(1).getEffectiveDate().compareTo(result.get(2).getEffectiveDate()) >= 0, 
                "Second policy should have later or equal effectiveDate than third");

        // Verify that policies with other locale are not returned
        result.forEach(policy -> {
            assertNotEquals(otherLocale, policy.getLocale(), 
                    "No policies with other locale should be returned");
        });
    }

    @Test
    void findActivePoliciesByTypeLocaleAndDate_NoActivePoliciesBeforeTodayForLocale_ReturnsEmptyList() {
        // Given: only inactive policies or policies with effectiveDate after today for the target locale
        LocalDate today = LocalDate.of(2024, 1, 15);
        LocalDate afterToday = LocalDate.of(2024, 1, 20);
        String targetLocale = "en-GB";
        String otherLocale = "fr-FR";

        ConsentPolicies inactiveBeforeTodayMatchingLocale = ConsentPolicies.builder()
                .policyType(testPolicyType)
                .version("1.0.0")
                .effectiveDate(LocalDate.of(2024, 1, 10))
                .contentHash("hash1")
                .policyUrl("https://example.com/policy1")
                .locale(targetLocale)
                .isActive(false)
                .build();

        ConsentPolicies activeAfterTodayMatchingLocale = ConsentPolicies.builder()
                .policyType(testPolicyType)
                .version("2.0.0")
                .effectiveDate(afterToday)
                .contentHash("hash2")
                .policyUrl("https://example.com/policy2")
                .locale(targetLocale)
                .isActive(true)
                .build();

        ConsentPolicies activeBeforeTodayOtherLocale = ConsentPolicies.builder()
                .policyType(testPolicyType)
                .version("1.5.0")
                .effectiveDate(LocalDate.of(2024, 1, 10))
                .contentHash("hash3")
                .policyUrl("https://example.com/policy3")
                .locale(otherLocale)
                .isActive(true)
                .build();

        // Persist policies
        entityManager.persistAndFlush(inactiveBeforeTodayMatchingLocale);
        entityManager.persistAndFlush(activeAfterTodayMatchingLocale);
        entityManager.persistAndFlush(activeBeforeTodayOtherLocale);
        entityManager.clear();

        // When: Call the repository method with target locale
        List<ConsentPolicies> result = consentPoliciesRepository.findActivePoliciesByTypeLocaleAndDate(testPolicyType, targetLocale, today);

        // Then: should return empty list
        assertTrue(result.isEmpty(), "Should return empty list when no active policies with effectiveDate <= today for the target locale");
    }

    @Test
    void findActivePoliciesByTypeLocaleAndDate_DifferentLocales_OnlyReturnsMatchingLocale() {
        // Given: policies with different locales
        LocalDate today = LocalDate.of(2024, 1, 15);
        LocalDate beforeToday = LocalDate.of(2024, 1, 10);

        ConsentPolicies enGBPolicy = ConsentPolicies.builder()
                .policyType(testPolicyType)
                .version("1.0.0")
                .effectiveDate(beforeToday)
                .contentHash("hash1")
                .policyUrl("https://example.com/policy1")
                .locale("en-GB")
                .isActive(true)
                .build();

        ConsentPolicies frFRPolicy = ConsentPolicies.builder()
                .policyType(testPolicyType)
                .version("1.0.0")
                .effectiveDate(beforeToday)
                .contentHash("hash2")
                .policyUrl("https://example.com/policy2")
                .locale("fr-FR")
                .isActive(true)
                .build();

        ConsentPolicies deDEPolicy = ConsentPolicies.builder()
                .policyType(testPolicyType)
                .version("1.0.0")
                .effectiveDate(beforeToday)
                .contentHash("hash3")
                .policyUrl("https://example.com/policy3")
                .locale("de-DE")
                .isActive(true)
                .build();

        // Persist policies
        entityManager.persistAndFlush(enGBPolicy);
        entityManager.persistAndFlush(frFRPolicy);
        entityManager.persistAndFlush(deDEPolicy);
        entityManager.clear();

        // When: Call the repository method for en-GB locale
        List<ConsentPolicies> enGBResult = consentPoliciesRepository.findActivePoliciesByTypeLocaleAndDate(testPolicyType, "en-GB", today);

        // Then: should return only en-GB policies
        assertEquals(1, enGBResult.size(), "Should return only one policy for en-GB locale");
        assertEquals("en-GB", enGBResult.get(0).getLocale(), 
                "Should return only en-GB locale");
        assertEquals("1.0.0", enGBResult.get(0).getVersion(), 
                "Should return the correct version");

        // When: Call the repository method for fr-FR locale
        List<ConsentPolicies> frFRResult = consentPoliciesRepository.findActivePoliciesByTypeLocaleAndDate(testPolicyType, "fr-FR", today);

        // Then: should return only fr-FR policies
        assertEquals(1, frFRResult.size(), "Should return only one policy for fr-FR locale");
        assertEquals("fr-FR", frFRResult.get(0).getLocale(), 
                "Should return only fr-FR locale");

        // When: Call the repository method for de-DE locale
        List<ConsentPolicies> deDEResult = consentPoliciesRepository.findActivePoliciesByTypeLocaleAndDate(testPolicyType, "de-DE", today);

        // Then: should return only de-DE policies
        assertEquals(1, deDEResult.size(), "Should return only one policy for de-DE locale");
        assertEquals("de-DE", deDEResult.get(0).getLocale(), 
                "Should return only de-DE locale");
    }

    @Test
    void findActivePoliciesByTypeLocaleAndDate_SameEffectiveDateAndLocale_ReturnsAllInCorrectOrder() {
        // Given: multiple policies with the same effectiveDate and locale
        LocalDate today = LocalDate.of(2024, 1, 15);
        LocalDate sameDate = LocalDate.of(2024, 1, 10);
        String targetLocale = "en-GB";

        ConsentPolicies policy1 = ConsentPolicies.builder()
                .policyType(testPolicyType)
                .version("1.0.0")
                .effectiveDate(sameDate)
                .contentHash("hash1")
                .policyUrl("https://example.com/policy1")
                .locale(targetLocale)
                .isActive(true)
                .build();

        ConsentPolicies policy2 = ConsentPolicies.builder()
                .policyType(testPolicyType)
                .version("1.1.0")
                .effectiveDate(sameDate)
                .contentHash("hash2")
                .policyUrl("https://example.com/policy2")
                .locale(targetLocale)
                .isActive(true)
                .build();

        ConsentPolicies policy3 = ConsentPolicies.builder()
                .policyType(testPolicyType)
                .version("1.2.0")
                .effectiveDate(sameDate)
                .contentHash("hash3")
                .policyUrl("https://example.com/policy3")
                .locale(targetLocale)
                .isActive(true)
                .build();

        // Persist policies in non-chronological order
        entityManager.persistAndFlush(policy2);
        entityManager.persistAndFlush(policy1);
        entityManager.persistAndFlush(policy3);
        entityManager.clear();

        // When: Call the repository method
        List<ConsentPolicies> result = consentPoliciesRepository.findActivePoliciesByTypeLocaleAndDate(testPolicyType, targetLocale, today);

        // Then: should return all 3 policies with same effectiveDate and locale
        assertEquals(3, result.size(), "Should return all 3 policies with same effectiveDate and locale");

        // Verify all have the same effectiveDate and locale
        result.forEach(policy -> {
            assertEquals(sameDate, policy.getEffectiveDate(), 
                    "All policies should have the same effectiveDate");
            assertEquals(targetLocale, policy.getLocale(), 
                    "All policies should have the same locale");
            assertTrue(policy.getIsActive(), "All policies should be active");
        });
    }
} 