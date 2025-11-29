package uk.gegc.kidsgptbackend.test;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

/**
 * Base class for unit tests.
 * <p>
 * Provides common setup and utilities for Mockito-based unit tests:
 * <ul>
 *   <li>Mockito extension for dependency injection</li>
 *   <li>Concurrent test execution enabled by default</li>
 *   <li>Lenient mock settings to avoid strict stubbing issues</li>
 *   <li>Helper methods for common test utilities (e.g., Clock mocking)</li>
 * </ul>
 * <p>
 * Usage:
 * <pre>
 * {@code
 * class MyUnitTest extends BaseUnitTest {
 *     @Mock
 *     private MyRepository repository;
 *
 *     @InjectMocks
 *     private MyService service;
 *
 *     @Test
 *     void myTest() {
 *         // Test implementation
 *     }
 * }
 * }
 * </pre>
 * <p>
 * Note: If your test requires strict mock verification or cannot run concurrently,
 * you can override the annotations in your test class.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@Execution(ExecutionMode.CONCURRENT)
public abstract class BaseUnitTest {

    /**
     * Sets up common test data before each test.
     * <p>
     * Subclasses can override this method to add additional setup.
     */
    @BeforeEach
    protected void setUp() {
        // Override in subclasses if needed
    }

    /**
     * Creates a fixed Clock instance for testing time-dependent code.
     * <p>
     * Useful for testing code that uses {@code Clock} to get current time,
     * allowing tests to have deterministic time values.
     *
     * @param instant the fixed instant to use
     * @return a Clock fixed to the specified instant
     */
    protected Clock createFixedClock(Instant instant) {
        return Clock.fixed(instant, ZoneOffset.UTC);
    }

    /**
     * Creates a fixed Clock instance for testing time-dependent code.
     * <p>
     * Uses a default test instant: "2024-01-01T12:00:00Z"
     *
     * @return a Clock fixed to the default test instant
     */
    protected Clock createDefaultFixedClock() {
        return createFixedClock(Instant.parse("2024-01-01T12:00:00Z"));
    }

    /**
     * Creates a fixed Clock instance for testing time-dependent code.
     * <p>
     * Convenience method that parses an ISO-8601 string.
     *
     * @param instantString the instant as an ISO-8601 string (e.g., "2024-01-01T12:00:00Z")
     * @return a Clock fixed to the specified instant
     */
    protected Clock createFixedClock(String instantString) {
        return createFixedClock(Instant.parse(instantString));
    }
}

