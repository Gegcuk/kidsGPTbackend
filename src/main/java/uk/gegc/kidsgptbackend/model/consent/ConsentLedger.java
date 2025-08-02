package uk.gegc.kidsgptbackend.model.consent;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "consent_ledger")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConsentLedger {
    
    @Id
    @Column(name = "consent_id", updatable = false, nullable = false)
    private UUID consentId;
    
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "consent_type", nullable = false)
    private ConsentType consentType;
    
    @Column(name = "consent_version", nullable = false, length = 64)
    private String consentVersion;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "consent_status", nullable = false)
    private ConsentStatus consentStatus;
    
    @Column(name = "policy_url", nullable = false, length = 512)
    private String policyUrl;
    
    @Column(name = "content_hash", nullable = false, length = 128)
    private String contentHash;
    
    @Column(name = "jurisdiction", nullable = false, length = 8)
    private String jurisdiction;
    
    @Column(name = "region", length = 16)
    private String region;
    
    @Column(name = "locale", length = 16)
    private String locale;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "lawful_basis", nullable = false)
    private LawfulBasis lawfulBasis;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false)
    private ConsentSource source;
    
    @Column(name = "ip_address", length = 64)
    private String ipAddress;
    
    @Column(name = "user_agent", length = 512)
    private String userAgent;
    
    @Column(name = "consent_timestamp", nullable = false)
    private LocalDateTime consentTimestamp;
    
    @Column(name = "parent_verification_id", updatable = false)
    private UUID parentVerificationId;
    
    @Column(name = "retention_expires_at", nullable = false)
    private LocalDateTime retentionExpiresAt;
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "receipt_json", nullable = false, columnDefinition = "JSON")
    private String receiptJson;
    
    @Column(name = "record_signature", nullable = false, columnDefinition = "VARBINARY(64)")
    private byte[] recordSignature;
    
    @Column(name = "withdrawn_consent_id")
    private UUID withdrawnConsentId;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "is_active_grant", insertable = false, updatable = false)
    private Boolean isActiveGrant;
    
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);
        }
    }
} 