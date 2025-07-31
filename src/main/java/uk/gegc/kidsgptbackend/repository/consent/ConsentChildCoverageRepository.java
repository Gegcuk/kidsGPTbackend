package uk.gegc.kidsgptbackend.repository.consent;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uk.gegc.kidsgptbackend.model.consent.ConsentChildCoverage;

import java.util.List;
import java.util.UUID;

@Repository
public interface ConsentChildCoverageRepository extends JpaRepository<ConsentChildCoverage, UUID> {
    
    List<ConsentChildCoverage> findByConsentId(UUID consentId);
    
    List<ConsentChildCoverage> findByKidId(UUID kidId);
    
    @Query("SELECT ccc FROM ConsentChildCoverage ccc WHERE ccc.kidId IN :kidIds")
    List<ConsentChildCoverage> findByKidIds(@Param("kidIds") List<UUID> kidIds);
    
    @Query("SELECT ccc.kidId FROM ConsentChildCoverage ccc WHERE ccc.consentId = :consentId")
    List<UUID> findKidIdsByConsentId(@Param("consentId") UUID consentId);
    
    @Query("SELECT ccc.consentId FROM ConsentChildCoverage ccc WHERE ccc.kidId = :kidId")
    List<UUID> findConsentIdsByKidId(@Param("kidId") UUID kidId);
} 