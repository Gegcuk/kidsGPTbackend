package uk.gegc.kidsgptbackend.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Slf4j
@Component
@ConditionalOnProperty(name = "spring.jpa.hibernate.ddl-auto", havingValue = "create-drop")
public class ConsentDatabaseInitializer {
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @PostConstruct
    public void initializeDatabase() {
        log.info("Initializing consent database constraints and triggers...");
        
        try {
            createAppendOnlyTrigger();
            createIndexes();
            log.info("Consent database initialization completed successfully");
        } catch (Exception e) {
            log.error("Failed to initialize consent database: {}", e.getMessage(), e);
            throw new RuntimeException("Database initialization failed", e);
        }
    }
    
    private void createAppendOnlyTrigger() {
        String triggerSql = """
            CREATE TRIGGER consent_ledger_prevent_update
            BEFORE UPDATE ON consent_ledger
            FOR EACH ROW
            BEGIN
              SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'consent_ledger is append-only';
            END
            """;
        
        try {
            jdbcTemplate.execute(triggerSql);
            log.info("Created append-only trigger for consent_ledger table");
        } catch (Exception e) {
            // Check if it's a "trigger already exists" error
            if (e.getMessage().contains("already exists") || e.getMessage().contains("Duplicate")) {
                log.info("Append-only trigger already exists, skipping creation");
            } else {
                log.warn("Failed to create append-only trigger: {}", e.getMessage());
                throw e;
            }
        }
    }
    
    private void createIndexes() {
        // Create any additional indexes that might be needed beyond what JPA creates
        String[] indexSqls = {
            // Index for efficient consent status queries
            "CREATE INDEX IF NOT EXISTS idx_consent_status_active ON consent_ledger (user_id, consent_type, consent_status) WHERE consent_status = 'GRANTED'",
            
            // Index for retention cleanup queries
            "CREATE INDEX IF NOT EXISTS idx_consent_retention_cleanup ON consent_ledger (retention_expires_at) WHERE retention_expires_at < NOW()",
            
            // Index for verification status queries
            "CREATE INDEX IF NOT EXISTS idx_verification_status ON parent_verification (verification_status, expires_at)",
            
            // Index for jurisdiction lookups
            "CREATE INDEX IF NOT EXISTS idx_jurisdiction_lookup ON jurisdiction_rules (country, region)"
        };
        
        for (String indexSql : indexSqls) {
            try {
                jdbcTemplate.execute(indexSql);
                log.debug("Created index: {}", indexSql);
            } catch (Exception e) {
                // Index might already exist or not supported in this MySQL version
                log.debug("Index creation skipped (might already exist): {}", e.getMessage());
            }
        }
    }
} 