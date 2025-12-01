package uk.gegc.kidsgptbackend.features.consent.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "consent_policies")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConsentPolicies {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "policy_id", updatable = false, nullable = false)
    private UUID policyId;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "policy_type", nullable = false)
    private ConsentType policyType;
    
    @Column(name = "version", nullable = false, length = 64)
    private String version;
    
    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;
    
    @Column(name = "content_hash", nullable = false, length = 128)
    private String contentHash;
    
    @Column(name = "policy_url", nullable = false, length = 512)
    private String policyUrl;
    
    @Column(name = "locale", length = 16)
    private String locale;
    
    @Column(name = "is_active", nullable = false)
    private Boolean isActive;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        if (isActive == null) {
            isActive = false;
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);
        }
    }
} 