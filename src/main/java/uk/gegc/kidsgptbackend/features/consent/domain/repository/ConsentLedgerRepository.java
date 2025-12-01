package uk.gegc.kidsgptbackend.features.consent.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentLedger;
import uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentStatus;
import uk.gegc.kidsgptbackend.features.consent.domain.model.ConsentType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConsentLedgerRepository extends JpaRepository<ConsentLedger, UUID> {

    /**
     * Find all consent ledger entries for a user ordered by consent timestamp (canonical event time) with deterministic tie-breaker
     */
    Page<ConsentLedger> findByUserIdOrderByConsentTimestampDescCreatedAtDesc(UUID userId, Pageable pageable);
    
    /**
     * Find consent ledger entries for a user and consent type ordered by consent timestamp (canonical event time) with deterministic tie-breaker
     */
    List<ConsentLedger> findByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc(UUID userId, ConsentType consentType);
    
    /**
     * Find the most recent consent ledger entry for a user and consent type ordered by consent timestamp (canonical event time) with deterministic tie-breaker
     */
    Optional<ConsentLedger> findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc(UUID userId, ConsentType consentType);
    
    /**
     * Find the most recent consent ledger entry for a user, consent type, and status ordered by consent timestamp (canonical event time) with deterministic tie-breaker
     */
    Optional<ConsentLedger> findFirstByUserIdAndConsentTypeAndConsentStatusOrderByConsentTimestampDescCreatedAtDesc(
            UUID userId, ConsentType consentType, ConsentStatus consentStatus);
    
    List<ConsentLedger> findByParentVerificationId(UUID parentVerificationId);
    
    @Query("SELECT cl FROM ConsentLedger cl WHERE cl.retentionExpiresAt <= :now")
    List<ConsentLedger> findExpiredConsents(@Param("now") LocalDateTime now);
    
    @Query("SELECT cl FROM ConsentLedger cl WHERE cl.userId = :userId AND cl.consentType = :consentType AND cl.consentVersion = :version AND cl.consentStatus = 'GRANTED'")
    Optional<ConsentLedger> findActiveGrantByUserTypeAndVersion(
            @Param("userId") UUID userId, 
            @Param("consentType") ConsentType consentType, 
            @Param("version") String version);
    
    @Query("SELECT COUNT(cl) FROM ConsentLedger cl WHERE cl.userId = :userId AND cl.consentType = :consentType AND cl.consentStatus = 'GRANTED'")
    long countActiveGrantsByUserAndType(@Param("userId") UUID userId, @Param("consentType") ConsentType consentType);
    
    List<ConsentLedger> findByJurisdictionAndRegion(String jurisdiction, String region);
    
    List<ConsentLedger> findByConsentTimestampBetween(LocalDateTime fromDate, LocalDateTime toDate);

    @Query("SELECT COUNT(cl) > 0 FROM ConsentLedger cl WHERE cl.userId = :userId AND cl.consentType = :consentType AND cl.consentVersion = :version AND cl.consentStatus = 'WITHDRAWN'")
    boolean existsWithdrawalByUserTypeAndVersion(@Param("userId") UUID userId, @Param("consentType") ConsentType consentType, @Param("version") String version);
} 