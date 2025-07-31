package uk.gegc.kidsgptbackend.repository.consent;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uk.gegc.kidsgptbackend.model.consent.ParentVerification;
import uk.gegc.kidsgptbackend.model.consent.VerificationStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ParentVerificationRepository extends JpaRepository<ParentVerification, UUID> {
    
    List<ParentVerification> findByParentIdOrderByCreatedAtDesc(UUID parentId);
    
    Optional<ParentVerification> findByVerificationIdAndVerificationStatus(UUID verificationId, VerificationStatus status);
    
    @Query("SELECT pv FROM ParentVerification pv WHERE pv.parentId = :parentId AND pv.verificationStatus = 'PENDING' AND pv.expiresAt > :now ORDER BY pv.createdAt DESC")
    List<ParentVerification> findPendingVerificationsByParent(@Param("parentId") UUID parentId, @Param("now") LocalDateTime now);
    
    @Query("SELECT COUNT(pv) FROM ParentVerification pv WHERE pv.parentId = :parentId AND pv.verificationStatus = 'FAILED' AND pv.createdAt >= :since")
    long countFailedAttemptsSince(@Param("parentId") UUID parentId, @Param("since") LocalDateTime since);
    
    @Query("SELECT pv FROM ParentVerification pv WHERE pv.expiresAt <= :now AND pv.verificationStatus = 'PENDING'")
    List<ParentVerification> findExpiredPendingVerifications(@Param("now") LocalDateTime now);
    
    List<ParentVerification> findByVerificationStatus(VerificationStatus status);
} 