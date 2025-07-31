package uk.gegc.kidsgptbackend.repository.consent;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uk.gegc.kidsgptbackend.model.consent.JurisdictionRules;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface JurisdictionRulesRepository extends JpaRepository<JurisdictionRules, UUID> {
    
    Optional<JurisdictionRules> findByCountryAndRegion(String country, String region);
    
    Optional<JurisdictionRules> findByCountryAndRegionIsNull(String country);
    
    @Query("SELECT jr FROM JurisdictionRules jr WHERE jr.country = :country AND (jr.region = :region OR jr.region IS NULL) ORDER BY jr.region NULLS LAST")
    List<JurisdictionRules> findByCountryAndRegionOrNull(@Param("country") String country, @Param("region") String region);
    
    List<JurisdictionRules> findByCountry(String country);
    
    List<JurisdictionRules> findByTeenOptInTrue();
    
    @Query("SELECT jr FROM JurisdictionRules jr WHERE jr.minorThreshold <= :age")
    List<JurisdictionRules> findByMinorThresholdLessThanOrEqualTo(@Param("age") Integer age);
} 