package uk.gegc.kidsgptbackend.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import uk.gegc.kidsgptbackend.model.user.Role;
import uk.gegc.kidsgptbackend.model.user.RoleName;
import uk.gegc.kidsgptbackend.repository.user.RoleRepository;

/**
 * Base class for integration tests.
 * <p>
 * Provides common setup and utilities for Spring Boot integration tests:
 * <ul>
 *   <li>Common Spring Boot test annotations</li>
 *   <li>Autowired MockMvc, ObjectMapper, and common repositories</li>
 *   <li>Helper methods for test data setup</li>
 * </ul>
 * <p>
 * Usage:
 * <pre>
 * {@code
 * class MyIntegrationTest extends BaseIntegrationTest {
 *     @Test
 *     void myTest() {
 *         // Test implementation
 *     }
 * }
 * }
 * </pre>
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
public abstract class BaseIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected RoleRepository roleRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    /**
     * Sets up common test data before each test.
     * Ensures that all required roles exist in the database.
     * <p>
     * Subclasses can override this method to add additional setup,
     * but should call {@code super.setUp()} to ensure roles are created.
     */
    @BeforeEach
    void setUp() {
        ensureRoleExists(RoleName.ROLE_ADMIN);
        ensureRoleExists(RoleName.ROLE_PARENT);
        ensureRoleExists(RoleName.ROLE_CHILD);
    }

    /**
     * Ensures that a role exists in the database.
     * If the role doesn't exist, it will be created.
     *
     * @param roleName the role name to ensure exists
     */
    protected void ensureRoleExists(RoleName roleName) {
        roleRepository.findByRole(roleName.name()).orElseGet(() -> {
            Role role = new Role();
            role.setRole(roleName.name());
            return roleRepository.save(role);
        });
    }
}

