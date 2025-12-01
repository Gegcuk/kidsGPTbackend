package uk.gegc.kidsgptbackend.features.consent.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Entity
@Table(name = "jurisdiction_rules")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JurisdictionRules {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "rule_id", updatable = false, nullable = false)
    private UUID ruleId;
    
    @Column(name = "country", nullable = false, length = 8)
    private String country;
    
    @Column(name = "region", length = 16)
    private String region;
    
    @Column(name = "minor_threshold", nullable = false)
    private Integer minorThreshold;
    
    @Column(name = "retention_years")
    private Integer retentionYears;
    
    @Column(name = "teen_opt_in", nullable = false)
    private Boolean teenOptIn;
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "allowed_methods", nullable = false, columnDefinition = "JSON")
    private String allowedMethods;
    
    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;
    
    @PrePersist
    protected void onCreate() {
        if (teenOptIn == null) {
            teenOptIn = false;
        }
    }
} 