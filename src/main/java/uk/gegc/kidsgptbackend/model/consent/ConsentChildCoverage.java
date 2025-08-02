package uk.gegc.kidsgptbackend.model.consent;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "consent_child_coverage")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConsentChildCoverage {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "coverage_id", updatable = false, nullable = false)
    private UUID coverageId;
    
    @Column(name = "consent_id", nullable = false, updatable = false)
    private UUID consentId;
    
    @Column(name = "kid_id", nullable = false, updatable = false)
    private UUID kidId;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
} 