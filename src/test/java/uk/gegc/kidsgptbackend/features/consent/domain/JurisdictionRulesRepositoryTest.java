package uk.gegc.kidsgptbackend.features.consent.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uk.gegc.kidsgptbackend.features.consent.domain.model.JurisdictionRules;
import uk.gegc.kidsgptbackend.features.consent.domain.repository.JurisdictionRulesRepository;
import uk.gegc.kidsgptbackend.test.BaseRepositoryTest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JurisdictionRules Repository Tests")
class JurisdictionRulesRepositoryTest extends BaseRepositoryTest {

    @Autowired
    private JurisdictionRulesRepository jurisdictionRulesRepository;

    private String testCountry;
    private String testRegion;

    @Override
    @BeforeEach
    protected void setUp() {
        super.setUp();
        testCountry = "GB";
        testRegion = "ENGLAND";
    }

    @Test
    @DisplayName("save: should persist JurisdictionRules correctly")
    void save_shouldPersistCorrectly() {
        // Given
        JurisdictionRules rule = JurisdictionRules.builder()
                .country(testCountry)
                .region(testRegion)
                .minorThreshold(18)
                .retentionYears(7)
                .teenOptIn(true)
                .allowedMethods("[\"EMAIL\", \"SMS\"]")
                .notes("UK jurisdiction rules")
                .build();

        // When
        JurisdictionRules saved = persistFlushAndClear(rule);
        Optional<JurisdictionRules> found = jurisdictionRulesRepository.findById(saved.getRuleId());

        // Then
        assertThat(saved.getRuleId()).isNotNull();
        assertThat(saved.getCountry()).isEqualTo(testCountry);
        assertThat(saved.getRegion()).isEqualTo(testRegion);
        assertThat(saved.getMinorThreshold()).isEqualTo(18);
        assertThat(saved.getRetentionYears()).isEqualTo(7);
        assertThat(saved.getTeenOptIn()).isTrue();
        assertThat(saved.getAllowedMethods()).isEqualTo("[\"EMAIL\", \"SMS\"]");
        assertThat(saved.getNotes()).isEqualTo("UK jurisdiction rules");
        
        assertThat(found).isPresent();
        assertThat(found.get().getRuleId()).isEqualTo(saved.getRuleId());
    }

    @Test
    @DisplayName("onCreate: should auto-populate teenOptIn to false when null")
    void onCreate_shouldAutoPopulateTeenOptIn() {
        // Given
        JurisdictionRules rule = JurisdictionRules.builder()
                .country(testCountry)
                .region(testRegion)
                .minorThreshold(18)
                .retentionYears(7)
                .teenOptIn(null) // Should be auto-populated
                .allowedMethods("[\"EMAIL\"]")
                .build();

        // When
        JurisdictionRules saved = persistFlushAndClear(rule);
        Optional<JurisdictionRules> found = jurisdictionRulesRepository.findById(saved.getRuleId());

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getTeenOptIn()).isFalse();
    }

    @Test
    @DisplayName("findByCountryAndRegion: should find rule by country and region")
    void findByCountryAndRegion_shouldFindByBothCriteria() {
        // Given
        JurisdictionRules rule = JurisdictionRules.builder()
                .country(testCountry)
                .region(testRegion)
                .minorThreshold(18)
                .retentionYears(7)
                .teenOptIn(false)
                .allowedMethods("[\"EMAIL\"]")
                .build();
        
        persistFlushAndClear(rule);

        // When
        Optional<JurisdictionRules> found = jurisdictionRulesRepository.findByCountryAndRegion(testCountry, testRegion);
        Optional<JurisdictionRules> notFound = jurisdictionRulesRepository.findByCountryAndRegion("US", "CA");

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getCountry()).isEqualTo(testCountry);
        assertThat(found.get().getRegion()).isEqualTo(testRegion);
        assertThat(notFound).isEmpty();
    }

    @Test
    @DisplayName("findByCountryAndRegionIsNull: should find rule by country with null region")
    void findByCountryAndRegionIsNull_shouldFindByCountryWithNullRegion() {
        // Given
        JurisdictionRules ruleWithRegion = JurisdictionRules.builder()
                .country(testCountry)
                .region(testRegion)
                .minorThreshold(18)
                .retentionYears(7)
                .teenOptIn(false)
                .allowedMethods("[\"EMAIL\"]")
                .build();
        
        JurisdictionRules ruleWithoutRegion = JurisdictionRules.builder()
                .country(testCountry)
                .region(null) // No region
                .minorThreshold(18)
                .retentionYears(7)
                .teenOptIn(false)
                .allowedMethods("[\"EMAIL\"]")
                .build();
        
        persistFlushAndClear(ruleWithRegion);
        persistFlushAndClear(ruleWithoutRegion);

        // When
        Optional<JurisdictionRules> found = jurisdictionRulesRepository.findByCountryAndRegionIsNull(testCountry);
        Optional<JurisdictionRules> notFound = jurisdictionRulesRepository.findByCountryAndRegionIsNull("US");

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getCountry()).isEqualTo(testCountry);
        assertThat(found.get().getRegion()).isNull();
        assertThat(notFound).isEmpty();
    }

    @Test
    @DisplayName("findByCountry: should return all rules for a country")
    void findByCountry_shouldReturnAllRulesForCountry() {
        // Given
        JurisdictionRules rule1 = JurisdictionRules.builder()
                .country(testCountry)
                .region("ENGLAND")
                .minorThreshold(18)
                .retentionYears(7)
                .teenOptIn(false)
                .allowedMethods("[\"EMAIL\"]")
                .build();
        
        JurisdictionRules rule2 = JurisdictionRules.builder()
                .country(testCountry)
                .region("SCOTLAND")
                .minorThreshold(18)
                .retentionYears(7)
                .teenOptIn(false)
                .allowedMethods("[\"EMAIL\"]")
                .build();
        
        JurisdictionRules otherCountry = JurisdictionRules.builder()
                .country("US")
                .region("CA")
                .minorThreshold(18)
                .retentionYears(7)
                .teenOptIn(false)
                .allowedMethods("[\"EMAIL\"]")
                .build();
        
        persistFlushAndClear(rule1);
        persistFlushAndClear(rule2);
        persistFlushAndClear(otherCountry);

        // When
        List<JurisdictionRules> gbRules = jurisdictionRulesRepository.findByCountry(testCountry);
        List<JurisdictionRules> usRules = jurisdictionRulesRepository.findByCountry("US");

        // Then
        assertThat(gbRules).hasSize(2);
        assertThat(gbRules).extracting(JurisdictionRules::getCountry).containsOnly(testCountry);
        assertThat(usRules).hasSize(1);
        assertThat(usRules.get(0).getCountry()).isEqualTo("US");
    }

    @Test
    @DisplayName("findByCountryAndRegionOrNull: should return matching region or null region rules")
    void findByCountryAndRegionOrNull_shouldReturnMatchingOrNullRegion() {
        // Given
        JurisdictionRules ruleWithRegion = JurisdictionRules.builder()
                .country(testCountry)
                .region(testRegion)
                .minorThreshold(18)
                .retentionYears(7)
                .teenOptIn(false)
                .allowedMethods("[\"EMAIL\"]")
                .build();
        
        JurisdictionRules ruleWithoutRegion = JurisdictionRules.builder()
                .country(testCountry)
                .region(null)
                .minorThreshold(16)
                .retentionYears(5)
                .teenOptIn(true)
                .allowedMethods("[\"SMS\"]")
                .build();
        
        JurisdictionRules otherRegion = JurisdictionRules.builder()
                .country(testCountry)
                .region("SCOTLAND")
                .minorThreshold(18)
                .retentionYears(7)
                .teenOptIn(false)
                .allowedMethods("[\"EMAIL\"]")
                .build();
        
        persistFlushAndClear(ruleWithRegion);
        persistFlushAndClear(ruleWithoutRegion);
        persistFlushAndClear(otherRegion);

        // When
        List<JurisdictionRules> results = jurisdictionRulesRepository.findByCountryAndRegionOrNull(testCountry, testRegion);

        // Then
        assertThat(results).hasSize(2);
        assertThat(results).extracting(JurisdictionRules::getRegion)
                .containsExactlyInAnyOrder(testRegion, null);
    }

    @Test
    @DisplayName("findByTeenOptInTrue: should return only rules with teenOptIn true")
    void findByTeenOptInTrue_shouldReturnOnlyTrueRules() {
        // Given
        JurisdictionRules withOptIn = JurisdictionRules.builder()
                .country(testCountry)
                .region(testRegion)
                .minorThreshold(16)
                .retentionYears(5)
                .teenOptIn(true)
                .allowedMethods("[\"EMAIL\"]")
                .build();
        
        JurisdictionRules withoutOptIn = JurisdictionRules.builder()
                .country("US")
                .region("CA")
                .minorThreshold(18)
                .retentionYears(7)
                .teenOptIn(false)
                .allowedMethods("[\"EMAIL\"]")
                .build();
        
        persistFlushAndClear(withOptIn);
        persistFlushAndClear(withoutOptIn);

        // When
        List<JurisdictionRules> results = jurisdictionRulesRepository.findByTeenOptInTrue();

        // Then
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getTeenOptIn()).isTrue();
        assertThat(results.get(0).getCountry()).isEqualTo(testCountry);
    }

    @Test
    @DisplayName("findByMinorThresholdLessThanOrEqualTo: should return rules with threshold <= age")
    void findByMinorThresholdLessThanOrEqualTo_shouldReturnRulesWithThresholdLessThanOrEqual() {
        // Given
        JurisdictionRules threshold16 = JurisdictionRules.builder()
                .country(testCountry)
                .region(testRegion)
                .minorThreshold(16)
                .retentionYears(5)
                .teenOptIn(true)
                .allowedMethods("[\"EMAIL\"]")
                .build();
        
        JurisdictionRules threshold18 = JurisdictionRules.builder()
                .country("US")
                .region("CA")
                .minorThreshold(18)
                .retentionYears(7)
                .teenOptIn(false)
                .allowedMethods("[\"EMAIL\"]")
                .build();
        
        JurisdictionRules threshold21 = JurisdictionRules.builder()
                .country("US")
                .region("NY")
                .minorThreshold(21)
                .retentionYears(7)
                .teenOptIn(false)
                .allowedMethods("[\"EMAIL\"]")
                .build();
        
        persistFlushAndClear(threshold16);
        persistFlushAndClear(threshold18);
        persistFlushAndClear(threshold21);

        // When
        List<JurisdictionRules> results18 = jurisdictionRulesRepository.findByMinorThresholdLessThanOrEqualTo(18);
        List<JurisdictionRules> results16 = jurisdictionRulesRepository.findByMinorThresholdLessThanOrEqualTo(16);

        // Then
        assertThat(results18).hasSize(2); // threshold16 and threshold18
        assertThat(results18).extracting(JurisdictionRules::getMinorThreshold)
                .containsExactlyInAnyOrder(16, 18);
        
        assertThat(results16).hasSize(1); // Only threshold16
        assertThat(results16.get(0).getMinorThreshold()).isEqualTo(16);
    }

    @Test
    @DisplayName("save: should handle null fields correctly")
    void save_shouldHandleNullFields() {
        // Given
        JurisdictionRules rule = JurisdictionRules.builder()
                .country(testCountry)
                .region(null)
                .minorThreshold(18)
                .retentionYears(null)
                .teenOptIn(null)
                .allowedMethods("[\"EMAIL\"]")
                .notes(null)
                .build();

        // When
        JurisdictionRules saved = persistFlushAndClear(rule);
        Optional<JurisdictionRules> found = jurisdictionRulesRepository.findById(saved.getRuleId());

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getRegion()).isNull();
        assertThat(found.get().getRetentionYears()).isNull();
        assertThat(found.get().getTeenOptIn()).isFalse(); // Auto-populated
        assertThat(found.get().getNotes()).isNull();
    }

    @Test
    @DisplayName("update: should update existing rule")
    void update_shouldUpdateExistingRule() {
        // Given
        JurisdictionRules rule = JurisdictionRules.builder()
                .country(testCountry)
                .region(testRegion)
                .minorThreshold(18)
                .retentionYears(7)
                .teenOptIn(false)
                .allowedMethods("[\"EMAIL\"]")
                .build();
        
        JurisdictionRules saved = persistFlushAndClear(rule);

        // When
        saved.setMinorThreshold(16);
        saved.setTeenOptIn(true);
        saved.setNotes("Updated notes");
        JurisdictionRules updated = jurisdictionRulesRepository.save(saved);
        flush();
        clear();
        Optional<JurisdictionRules> found = jurisdictionRulesRepository.findById(updated.getRuleId());

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getMinorThreshold()).isEqualTo(16);
        assertThat(found.get().getTeenOptIn()).isTrue();
        assertThat(found.get().getNotes()).isEqualTo("Updated notes");
    }

    @Test
    @DisplayName("delete: should remove rule from database")
    void delete_shouldRemoveRule() {
        // Given
        JurisdictionRules rule = JurisdictionRules.builder()
                .country(testCountry)
                .region(testRegion)
                .minorThreshold(18)
                .retentionYears(7)
                .teenOptIn(false)
                .allowedMethods("[\"EMAIL\"]")
                .build();
        
        JurisdictionRules saved = persistFlushAndClear(rule);

        // When
        jurisdictionRulesRepository.delete(saved);
        flush();
        clear();
        Optional<JurisdictionRules> found = jurisdictionRulesRepository.findById(saved.getRuleId());

        // Then
        assertThat(found).isEmpty();
    }
}

