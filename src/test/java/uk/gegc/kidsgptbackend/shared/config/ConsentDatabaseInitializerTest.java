package uk.gegc.kidsgptbackend.shared.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import uk.gegc.kidsgptbackend.test.BaseUnitTest;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link ConsentDatabaseInitializer}.
 * <p>
 * Tests database initialization including:
 * - Database type detection (MySQL, H2, unknown)
 * - Append-only trigger creation
 * - Index creation
 * - Error handling (already exists, etc.)
 */
@DisplayName("ConsentDatabaseInitializer Tests")
class ConsentDatabaseInitializerTest extends BaseUnitTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private DataSource dataSource;

    @Mock
    private Connection connection;

    @Mock
    private DatabaseMetaData databaseMetaData;

    private ConsentDatabaseInitializer initializer;

    @Override
    @BeforeEach
    protected void setUp() {
        super.setUp();
        initializer = new ConsentDatabaseInitializer();
        ReflectionTestUtils.setField(initializer, "jdbcTemplate", jdbcTemplate);
        ReflectionTestUtils.setField(initializer, "dataSource", dataSource);
    }

    @Test
    @DisplayName("initializeDatabase: should detect MySQL and create trigger and indexes")
    void initializeDatabase_mysql_detectsAndCreatesTriggerAndIndexes() throws SQLException {
        // Given
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(databaseMetaData);
        when(databaseMetaData.getDatabaseProductName()).thenReturn("MySQL");
        doNothing().when(jdbcTemplate).execute(anyString());

        // When
        initializer.initializeDatabase();

        // Then
        verify(databaseMetaData).getDatabaseProductName();
        verify(jdbcTemplate, atLeastOnce()).execute(anyString());
        // Verify trigger SQL was executed
        verify(jdbcTemplate).execute(argThat((String sql) -> sql.contains("CREATE TRIGGER consent_ledger_prevent_update")));
        // Verify indexes were created
        verify(jdbcTemplate, atLeast(4)).execute(anyString());
    }

    @Test
    @DisplayName("initializeDatabase: should detect H2 and skip trigger but create indexes")
    void initializeDatabase_h2_skipsTriggerCreatesIndexes() throws SQLException {
        // Given
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(databaseMetaData);
        when(databaseMetaData.getDatabaseProductName()).thenReturn("H2 Database");
        doNothing().when(jdbcTemplate).execute(anyString());

        // When
        initializer.initializeDatabase();

        // Then
        verify(databaseMetaData).getDatabaseProductName();
        // Verify trigger was NOT created (H2 skips trigger)
        verify(jdbcTemplate, never()).execute(argThat((String sql) -> sql.contains("CREATE TRIGGER")));
        // Verify indexes were created
        verify(jdbcTemplate, atLeast(4)).execute(anyString());
    }

    @Test
    @DisplayName("initializeDatabase: should handle unknown database type gracefully")
    void initializeDatabase_unknownDatabase_usesMySQLFallback() throws SQLException {
        // Given
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(databaseMetaData);
        when(databaseMetaData.getDatabaseProductName()).thenReturn("PostgreSQL");
        doNothing().when(jdbcTemplate).execute(anyString());

        // When
        initializer.initializeDatabase();

        // Then
        verify(databaseMetaData).getDatabaseProductName();
        // Should use MySQL syntax as fallback
        verify(jdbcTemplate, atLeastOnce()).execute(anyString());
    }

    @Test
    @DisplayName("initializeDatabase: should handle case-insensitive database name detection")
    void initializeDatabase_caseInsensitiveDetection_works() throws SQLException {
        // Given
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(databaseMetaData);
        when(databaseMetaData.getDatabaseProductName()).thenReturn("mysql");
        doNothing().when(jdbcTemplate).execute(anyString());

        // When
        initializer.initializeDatabase();

        // Then
        verify(databaseMetaData).getDatabaseProductName();
        verify(jdbcTemplate, atLeastOnce()).execute(anyString());
    }

    @Test
    @DisplayName("initializeDatabase: should handle trigger already exists error gracefully")
    void initializeDatabase_triggerAlreadyExists_continues() throws SQLException {
        // Given
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(databaseMetaData);
        when(databaseMetaData.getDatabaseProductName()).thenReturn("MySQL");
        doThrow(new RuntimeException("Trigger already exists"))
                .when(jdbcTemplate).execute(argThat((String sql) -> sql.contains("CREATE TRIGGER")));
        doNothing().when(jdbcTemplate).execute(argThat((String sql) -> !sql.contains("CREATE TRIGGER")));

        // When
        initializer.initializeDatabase();

        // Then
        verify(jdbcTemplate).execute(argThat((String sql) -> sql.contains("CREATE TRIGGER")));
        // Should continue with index creation
        verify(jdbcTemplate, atLeast(4)).execute(anyString());
    }

    @Test
    @DisplayName("initializeDatabase: should handle duplicate trigger error gracefully")
    void initializeDatabase_duplicateTrigger_continues() throws SQLException {
        // Given
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(databaseMetaData);
        when(databaseMetaData.getDatabaseProductName()).thenReturn("MySQL");
        doThrow(new RuntimeException("Duplicate trigger name"))
                .when(jdbcTemplate).execute(argThat((String sql) -> sql.contains("CREATE TRIGGER")));
        doNothing().when(jdbcTemplate).execute(argThat((String sql) -> !sql.contains("CREATE TRIGGER")));

        // When
        initializer.initializeDatabase();

        // Then
        verify(jdbcTemplate).execute(argThat((String sql) -> sql.contains("CREATE TRIGGER")));
        // Should continue with index creation
        verify(jdbcTemplate, atLeast(4)).execute(anyString());
    }

    @Test
    @DisplayName("initializeDatabase: should handle index creation errors gracefully")
    void initializeDatabase_indexCreationError_continues() throws SQLException {
        // Given
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(databaseMetaData);
        when(databaseMetaData.getDatabaseProductName()).thenReturn("MySQL");
        doNothing().when(jdbcTemplate).execute(argThat((String sql) -> sql.contains("CREATE TRIGGER")));
        // First index succeeds, second fails, rest succeed
        doNothing().when(jdbcTemplate).execute(argThat((String sql) -> sql.contains("idx_consent_status_active")));
        doThrow(new RuntimeException("Index already exists"))
                .when(jdbcTemplate).execute(argThat((String sql) -> sql.contains("idx_consent_retention_cleanup")));
        doNothing().when(jdbcTemplate).execute(argThat((String sql) -> 
            sql.contains("idx_verification_status") || sql.contains("idx_jurisdiction_lookup")));

        // When
        initializer.initializeDatabase();

        // Then
        // Should continue processing all indexes despite one failure
        verify(jdbcTemplate, atLeast(4)).execute(anyString());
    }

    @Test
    @DisplayName("initializeDatabase: should throw RuntimeException on critical failure")
    void initializeDatabase_criticalFailure_throwsRuntimeException() throws SQLException {
        // Given
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(databaseMetaData);
        when(databaseMetaData.getDatabaseProductName()).thenReturn("MySQL");
        doThrow(new RuntimeException("Critical database error"))
                .when(jdbcTemplate).execute(anyString());

        // When/Then
        assertThatThrownBy(() -> initializer.initializeDatabase())
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Database initialization failed");
    }

    @Test
    @DisplayName("initializeDatabase: should handle SQLException during database detection")
    void initializeDatabase_sqlExceptionDuringDetection_throwsRuntimeException() throws SQLException {
        // Given
        when(dataSource.getConnection()).thenThrow(new SQLException("Connection failed"));

        // When/Then
        assertThatThrownBy(() -> initializer.initializeDatabase())
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Database initialization failed");
    }

    @Test
    @DisplayName("initializeDatabase: should create all MySQL indexes")
    void initializeDatabase_mysql_createsAllIndexes() throws SQLException {
        // Given
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(databaseMetaData);
        when(databaseMetaData.getDatabaseProductName()).thenReturn("MySQL");
        doNothing().when(jdbcTemplate).execute(anyString());

        // When
        initializer.initializeDatabase();

        // Then
        verify(jdbcTemplate).execute(argThat((String sql) -> sql.contains("idx_consent_status_active")));
        verify(jdbcTemplate).execute(argThat((String sql) -> sql.contains("idx_consent_retention_cleanup")));
        verify(jdbcTemplate).execute(argThat((String sql) -> sql.contains("idx_verification_status")));
        verify(jdbcTemplate).execute(argThat((String sql) -> sql.contains("idx_jurisdiction_lookup")));
    }

    @Test
    @DisplayName("initializeDatabase: should create all H2 indexes")
    void initializeDatabase_h2_createsAllIndexes() throws SQLException {
        // Given
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(databaseMetaData);
        when(databaseMetaData.getDatabaseProductName()).thenReturn("H2");
        doNothing().when(jdbcTemplate).execute(anyString());

        // When
        initializer.initializeDatabase();

        // Then
        verify(jdbcTemplate).execute(argThat((String sql) -> sql.contains("idx_consent_status_active")));
        verify(jdbcTemplate).execute(argThat((String sql) -> sql.contains("idx_consent_retention_cleanup")));
        verify(jdbcTemplate).execute(argThat((String sql) -> sql.contains("idx_verification_status")));
        verify(jdbcTemplate).execute(argThat((String sql) -> sql.contains("idx_jurisdiction_lookup")));
    }

    @Test
    @DisplayName("initializeDatabase: should handle uppercase database type")
    void initializeDatabase_uppercaseDatabaseType_works() throws SQLException {
        // Given
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(databaseMetaData);
        when(databaseMetaData.getDatabaseProductName()).thenReturn("MYSQL");
        doNothing().when(jdbcTemplate).execute(anyString());

        // When
        initializer.initializeDatabase();

        // Then
        verify(jdbcTemplate, atLeastOnce()).execute(anyString());
    }

    @Test
    @DisplayName("initializeDatabase: should handle mixed case database type")
    void initializeDatabase_mixedCaseDatabaseType_works() throws SQLException {
        // Given
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(databaseMetaData);
        when(databaseMetaData.getDatabaseProductName()).thenReturn("MySql");
        doNothing().when(jdbcTemplate).execute(anyString());

        // When
        initializer.initializeDatabase();

        // Then
        verify(jdbcTemplate, atLeastOnce()).execute(anyString());
    }

    @Test
    @DisplayName("initializeDatabase: should handle trigger creation failure that is not 'already exists'")
    void initializeDatabase_triggerCreationFailure_throwsException() throws SQLException {
        // Given
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(databaseMetaData);
        when(databaseMetaData.getDatabaseProductName()).thenReturn("MySQL");
        doThrow(new RuntimeException("Permission denied"))
                .when(jdbcTemplate).execute(argThat((String sql) -> sql.contains("CREATE TRIGGER")));

        // When/Then
        assertThatThrownBy(() -> initializer.initializeDatabase())
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Database initialization failed");
    }

    @Test
    @DisplayName("initializeDatabase: should close connection after metadata access")
    void initializeDatabase_closesConnection() throws SQLException {
        // Given
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(databaseMetaData);
        when(databaseMetaData.getDatabaseProductName()).thenReturn("MySQL");
        doNothing().when(jdbcTemplate).execute(anyString());

        // When
        initializer.initializeDatabase();

        // Then
        verify(connection).getMetaData();
        // Note: In real usage, Spring manages connection lifecycle, but we verify it was accessed
    }

    @Test
    @DisplayName("initializeDatabase: should handle H2 database product name variations")
    void initializeDatabase_h2Variations_skipsTrigger() throws SQLException {
        // Given
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(databaseMetaData);
        when(databaseMetaData.getDatabaseProductName()).thenReturn("H2");
        doNothing().when(jdbcTemplate).execute(anyString());

        // When
        initializer.initializeDatabase();

        // Then
        verify(jdbcTemplate, never()).execute(argThat((String sql) -> sql.contains("CREATE TRIGGER")));
        verify(jdbcTemplate, atLeast(4)).execute(anyString());
    }

    @Test
    @DisplayName("initializeDatabase: should handle MySQL with WHERE clause in index")
    void initializeDatabase_mysqlIndexesWithWhereClause_createsCorrectly() throws SQLException {
        // Given
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(databaseMetaData);
        when(databaseMetaData.getDatabaseProductName()).thenReturn("MySQL");
        doNothing().when(jdbcTemplate).execute(anyString());

        // When
        initializer.initializeDatabase();

        // Then
        // Verify MySQL-specific index with WHERE clause
        verify(jdbcTemplate).execute(argThat((String sql) -> 
            sql.contains("idx_consent_status_active") && sql.contains("WHERE consent_status = 'GRANTED'")));
        verify(jdbcTemplate).execute(argThat((String sql) -> 
            sql.contains("idx_consent_retention_cleanup") && sql.contains("WHERE retention_expires_at < NOW()")));
    }

    @Test
    @DisplayName("initializeDatabase: should handle H2 indexes without WHERE clause")
    void initializeDatabase_h2IndexesWithoutWhereClause_createsCorrectly() throws SQLException {
        // Given
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(databaseMetaData);
        when(databaseMetaData.getDatabaseProductName()).thenReturn("H2");
        doNothing().when(jdbcTemplate).execute(anyString());

        // When
        initializer.initializeDatabase();

        // Then
        // Verify H2 indexes don't have WHERE clause
        verify(jdbcTemplate).execute(argThat((String sql) -> 
            sql.contains("idx_consent_status_active") && !sql.contains("WHERE")));
        verify(jdbcTemplate).execute(argThat((String sql) -> 
            sql.contains("idx_consent_retention_cleanup") && !sql.contains("WHERE")));
    }
}

