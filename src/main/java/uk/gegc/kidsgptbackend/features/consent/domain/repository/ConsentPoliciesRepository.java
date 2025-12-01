package uk.gegc.kidsgptbackend.features.consent.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentPolicies;
import uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConsentPoliciesRepository extends JpaRepository<ConsentPolicies, UUID> {
    
    List<ConsentPolicies> findByPolicyTypeAndIsActiveTrueOrderByEffectiveDateDesc(ConsentType policyType);
    
    List<ConsentPolicies> findByPolicyTypeAndLocaleAndIsActiveTrueOrderByEffectiveDateDesc(ConsentType policyType, String locale);
    
    Optional<ConsentPolicies> findByPolicyTypeAndVersionAndLocale(ConsentType policyType, String version, String locale);
    
    @Query("SELECT cp FROM ConsentPolicies cp WHERE cp.policyType = :policyType AND cp.effectiveDate <= :date AND cp.isActive = true ORDER BY cp.effectiveDate DESC")
    List<ConsentPolicies> findActivePoliciesByTypeAndDate(@Param("policyType") ConsentType policyType, @Param("date") LocalDate date);
    
    @Query("SELECT cp FROM ConsentPolicies cp WHERE cp.policyType = :policyType AND cp.locale = :locale AND cp.effectiveDate <= :date AND cp.isActive = true ORDER BY cp.effectiveDate DESC")
    List<ConsentPolicies> findActivePoliciesByTypeLocaleAndDate(
            @Param("policyType") ConsentType policyType, 
            @Param("locale") String locale, 
            @Param("date") LocalDate date);
    
    List<ConsentPolicies> findByIsActiveTrue();
    
    List<ConsentPolicies> findByPolicyType(ConsentType policyType);
} 