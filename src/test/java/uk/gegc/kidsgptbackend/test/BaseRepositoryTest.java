package uk.gegc.kidsgptbackend.test;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

/**
 * Base class for repository tests.
 * <p>
 * Provides common setup and utilities for JPA repository tests:
 * <ul>
 *   <li>DataJpaTest annotation for lightweight JPA testing</li>
 *   <li>Test profile activation</li>
 *   <li>Autowired TestEntityManager for entity persistence</li>
 *   <li>Helper methods for common repository test operations</li>
 * </ul>
 * <p>
 * Usage:
 * <pre>
 * {@code
 * class MyRepositoryTest extends BaseRepositoryTest {
 *     @Autowired
 *     private MyRepository repository;
 *
 *     @BeforeEach
 *     void setUp() {
 *         super.setUp();
 *         // Additional setup
 *     }
 *
 *     @Test
 *     void myTest() {
 *         // Use entityManager to persist test data
 *         MyEntity entity = new MyEntity();
 *         persistAndFlush(entity);
 *         // Test repository methods
 *     }
 * }
 * }
 * </pre>
 */
@DataJpaTest
@ActiveProfiles("test")
public abstract class BaseRepositoryTest {

    @Autowired
    protected TestEntityManager entityManager;

    /**
     * Sets up common test data before each test.
     * <p>
     * Subclasses can override this method to add additional setup,
     * but should call {@code super.setUp()} if they override.
     */
    @BeforeEach
    protected void setUp() {
        // Override in subclasses if needed
    }

    /**
     * Persists and flushes an entity to the test database.
     * <p>
     * This is a convenience method that wraps {@code entityManager.persistAndFlush()}.
     *
     * @param entity the entity to persist
     * @param <T> the entity type
     * @return the persisted entity (for method chaining)
     */
    protected <T> T persistAndFlush(T entity) {
        return entityManager.persistAndFlush(entity);
    }

    /**
     * Persists an entity to the test database without flushing.
     * <p>
     * This is a convenience method that wraps {@code entityManager.persist()}.
     *
     * @param entity the entity to persist
     * @param <T> the entity type
     * @return the persisted entity (for method chaining)
     */
    protected <T> T persist(T entity) {
        entityManager.persist(entity);
        return entity;
    }

    /**
     * Flushes the persistence context.
     * <p>
     * This is a convenience method that wraps {@code entityManager.flush()}.
     */
    protected void flush() {
        entityManager.flush();
    }

    /**
     * Clears the persistence context.
     * <p>
     * This is useful when you want to ensure that subsequent queries
     * fetch data from the database rather than from the persistence context.
     * This is a convenience method that wraps {@code entityManager.clear()}.
     */
    protected void clear() {
        entityManager.clear();
    }

    /**
     * Persists, flushes, and clears the persistence context.
     * <p>
     * This is a common pattern in repository tests where you want to:
     * 1. Persist an entity
     * 2. Flush to ensure it's written to the database
     * 3. Clear the persistence context to ensure subsequent queries hit the database
     *
     * @param entity the entity to persist
     * @param <T> the entity type
     * @return the persisted entity (for method chaining)
     */
    protected <T> T persistFlushAndClear(T entity) {
        entityManager.persistAndFlush(entity);
        entityManager.clear();
        return entity;
    }

    /**
     * Merges an entity with the persistence context.
     * <p>
     * This is a convenience method that wraps {@code entityManager.merge()}.
     *
     * @param entity the entity to merge
     * @param <T> the entity type
     * @return the merged entity
     */
    protected <T> T merge(T entity) {
        return entityManager.merge(entity);
    }

    /**
     * Removes an entity from the persistence context.
     * <p>
     * This is a convenience method that wraps {@code entityManager.remove()}.
     *
     * @param entity the entity to remove
     */
    protected void remove(Object entity) {
        entityManager.remove(entity);
    }

    /**
     * Finds an entity by its class and ID.
     * <p>
     * This is a convenience method that wraps {@code entityManager.find()}.
     *
     * @param entityClass the entity class
     * @param id the entity ID
     * @param <T> the entity type
     * @return the found entity, or null if not found
     */
    protected <T> T find(Class<T> entityClass, Object id) {
        return entityManager.find(entityClass, id);
    }
}

