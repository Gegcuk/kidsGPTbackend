package uk.gegc.kidsgptbackend.model.consent;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "parent_verification", 
    uniqueConstraints = {
        @UniqueConstraint(columnNames = {"parent_id", "verification_method", "contact_info_hash", "verification_status"}, 
                         name = "uk_parent_verification_unique_pending")
    },
    indexes = {
        @Index(columnList = "parent_id, verification_status, expires_at", 
               name = "idx_parent_verification_pending_expiry")
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ParentVerification {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "verification_id", updatable = false, nullable = false)
    private UUID verificationId;
    
    @Column(name = "parent_id", nullable = false, updatable = false)
    private UUID parentId;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "verification_method", nullable = false)
    private VerificationMethod verificationMethod;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false)
    private VerificationStatus verificationStatus;
    
    @Column(name = "contact_info_hash", nullable = false, columnDefinition = "VARBINARY(32)")
    private byte[] contactInfoHash;
    
    @Column(name = "verification_code_hash", nullable = false, columnDefinition = "VARBINARY(32)")
    private byte[] verificationCodeHash;
    
    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount;
    
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;
    
    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;
    
    @Column(name = "ip_address", length = 64)
    private String ipAddress;
    
    @Column(name = "user_agent", length = 512)
    private String userAgent;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        if (attemptCount == null) {
            attemptCount = 0;
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);
        }
    }
} 