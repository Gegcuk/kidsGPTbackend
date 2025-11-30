package uk.gegc.kidsgptbackend.shared.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

@Slf4j
@Component
@ConditionalOnProperty(name = "spring.jpa.hibernate.ddl-auto", havingValue = "create-drop")
public class ConsentDatabaseInitializer {
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @Autowired
    private DataSource dataSource;
    
    @PostConstruct
    public void initializeDatabase() {
        log.info("Initializing consent database constraints and triggers...");
        
        try {
            String databaseType = detectDatabaseType();
            log.info("Detected database type: {}", databaseType);
            
            createAppendOnlyTrigger(databaseType);
            createIndexes(databaseType);
            log.info("Consent database initialization completed successfully");
        } catch (Exception e) {
            log.error("Failed to initialize consent database: {}", e.getMessage(), e);
            throw new RuntimeException("Database initialization failed", e);
        }
    }
    
    private String detectDatabaseType() throws SQLException {
        DatabaseMetaData metaData = dataSource.getConnection().getMetaData();
        String databaseProductName = metaData.getDatabaseProductName();
        log.debug("Database product name: {}", databaseProductName);
        
        if (databaseProductName.toLowerCase().contains("mysql")) {
            return "MYSQL";
        } else if (databaseProductName.toLowerCase().contains("h2")) {
            return "H2";
        } else {
            log.warn("Unknown database type: {}. Using MySQL syntax as fallback.", databaseProductName);
            return "MYSQL";
        }
    }
    
    private void createAppendOnlyTrigger(String databaseType) {
        String triggerSql = null;
        
        switch (databaseType.toUpperCase()) {
            case "MYSQL":
                triggerSql = """
                    CREATE TRIGGER consent_ledger_prevent_update
                    BEFORE UPDATE ON consent_ledger
                    FOR EACH ROW
                    BEGIN
                      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'consent_ledger is append-only';
                    END
                    """;
                break;
                
            case "H2":
                // H2 doesn't support MySQL's SIGNAL syntax and has complex trigger requirements
                // For tests, we'll skip trigger creation and rely on application-level validation
                log.info("Skipping trigger creation for H2 database (tests only)");
                return;
                
            default:
                log.warn("Unknown database type: {}. Skipping trigger creation.", databaseType);
                return;
        }
        
        try {
            jdbcTemplate.execute(triggerSql);
            log.info("Created append-only trigger for consent_ledger table using {} syntax", databaseType);
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
    
    private void createIndexes(String databaseType) {
        // Create any additional indexes that might be needed beyond what JPA creates
        String[] indexSqls;
        
        switch (databaseType.toUpperCase()) {
            case "MYSQL":
                indexSqls = new String[]{
                    // Index for efficient consent status queries
                    "CREATE INDEX IF NOT EXISTS idx_consent_status_active ON consent_ledger (user_id, consent_type, consent_status) WHERE consent_status = 'GRANTED'",
                    
                    // Index for retention cleanup queries
                    "CREATE INDEX IF NOT EXISTS idx_consent_retention_cleanup ON consent_ledger (retention_expires_at) WHERE retention_expires_at < NOW()",
                    
                    // Index for verification status queries
                    "CREATE INDEX IF NOT EXISTS idx_verification_status ON parent_verification (verification_status, expires_at)",
                    
                    // Index for jurisdiction lookups
                    "CREATE INDEX IF NOT EXISTS idx_jurisdiction_lookup ON jurisdiction_rules (country, region)"
                };
                break;
                
            case "H2":
                // H2 doesn't support IF NOT EXISTS for indexes, so we use simpler syntax
                indexSqls = new String[]{
                    // Index for efficient consent status queries
                    "CREATE INDEX idx_consent_status_active ON consent_ledger (user_id, consent_type, consent_status)",
                    
                    // Index for retention cleanup queries
                    "CREATE INDEX idx_consent_retention_cleanup ON consent_ledger (retention_expires_at)",
                    
                    // Index for verification status queries
                    "CREATE INDEX idx_verification_status ON parent_verification (verification_status, expires_at)",
                    
                    // Index for jurisdiction lookups
                    "CREATE INDEX idx_jurisdiction_lookup ON jurisdiction_rules (country, region)"
                };
                break;
                
            default:
                log.warn("Unknown database type: {}. Skipping index creation.", databaseType);
                return;
        }
        
        for (String indexSql : indexSqls) {
            try {
                jdbcTemplate.execute(indexSql);
                log.debug("Created index: {}", indexSql);
            } catch (Exception e) {
                // Index might already exist or not supported in this database version
                log.debug("Index creation skipped (might already exist): {}", e.getMessage());
            }
        }
    }
} 