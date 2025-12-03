package uk.gegc.kidsgptbackend.integration;

import jakarta.persistence.EntityManager;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.Metamodel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import uk.gegc.kidsgptbackend.config.TestClockConfig;
import uk.gegc.kidsgptbackend.features.family.domain.model.Kid;
import uk.gegc.kidsgptbackend.features.family.domain.model.Parent;
import uk.gegc.kidsgptbackend.features.user.domain.model.AgeGroup;
import uk.gegc.kidsgptbackend.features.user.domain.model.Role;
import uk.gegc.kidsgptbackend.features.user.domain.model.RoleName;
import uk.gegc.kidsgptbackend.features.user.domain.model.User;
import uk.gegc.kidsgptbackend.features.family.domain.repository.KidRepository;
import uk.gegc.kidsgptbackend.features.family.domain.repository.ParentRepository;
import uk.gegc.kidsgptbackend.features.user.domain.repository.RoleRepository;
import uk.gegc.kidsgptbackend.features.user.domain.repository.UserRepository;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Import(TestClockConfig.class)
@DisplayName("Database Schema Integration Tests")
class DatabaseSchemaIntegrationTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private KidRepository kidRepository;

    @Autowired
    private ParentRepository parentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Test
    @DisplayName("Kids table should not have first_name and last_name fields")
    void kidsTableShouldNotHaveNameFields() {
        // Get the metamodel for the Kid entity
        Metamodel metamodel = entityManager.getMetamodel();
        EntityType<Kid> kidEntityType = metamodel.entity(Kid.class);
        
        // Verify that the Kid entity doesn't have first_name or last_name fields
        Set<String> attributeNames = kidEntityType.getDeclaredSingularAttributes().stream()
                .map(attr -> attr.getName())
                .collect(java.util.stream.Collectors.toSet());
        
        assertThat(attributeNames).doesNotContain("firstName");
        assertThat(attributeNames).doesNotContain("lastName");
        assertThat(attributeNames).doesNotContain("first_name");
        assertThat(attributeNames).doesNotContain("last_name");
        
        // Verify that the Kid entity has the correct fields for anonymous kids
        assertThat(attributeNames).contains("nickname");
        assertThat(attributeNames).contains("ageGroup");
        assertThat(attributeNames).contains("parent");
        assertThat(attributeNames).contains("user");
    }

    @Test
    @DisplayName("Kid entity should be able to persist without first_name and last_name")
    void kidEntityShouldPersistWithoutNameFields() {
        // Ensure roles exist
        Role childRole = roleRepository.findByRole(RoleName.ROLE_CHILD.name())
                .orElseGet(() -> {
                    Role role = new Role();
                    role.setRole(RoleName.ROLE_CHILD.name());
                    return roleRepository.save(role);
                });

        Role parentRole = roleRepository.findByRole(RoleName.ROLE_PARENT.name())
                .orElseGet(() -> {
                    Role role = new Role();
                    role.setRole(RoleName.ROLE_PARENT.name());
                    return roleRepository.save(role);
                });

        // Create parent user
        User parentUser = new User();
        parentUser.setUsername("testparent" + System.currentTimeMillis());
        parentUser.setEmail("parent@test.com");
        parentUser.setHashedPassword("hashedPassword");
        parentUser.setRoles(Set.of(parentRole));
        parentUser.setActive(true);
        parentUser = userRepository.save(parentUser);

        // Create parent profile
        Parent parent = new Parent();
        parent.setFirstName("Test");
        parent.setLastName("Parent");
        parent.setEmail("parent@test.com");
        parent = parentRepository.save(parent);

        // Create kid user
        User kidUser = new User();
        kidUser.setUsername("testkid" + System.currentTimeMillis());
        kidUser.setEmail("testkid@kid.local");
        kidUser.setHashedPassword("hashedPassword");
        kidUser.setRoles(Set.of(childRole));
        kidUser.setActive(true);
        kidUser = userRepository.save(kidUser);

        // Create kid - this should work without first_name and last_name
        Kid kid = new Kid();
        kid.setNickname("TestKid");
        kid.setAgeGroup(AgeGroup.AGE_6_8);
        kid.setParent(parent);
        kid.setUser(kidUser);

        // This should not throw an exception about missing first_name/last_name
        Kid savedKid = kidRepository.save(kid);
        
        assertThat(savedKid).isNotNull();
        assertThat(savedKid.getId()).isNotNull();
        assertThat(savedKid.getNickname()).isEqualTo("TestKid");
        assertThat(savedKid.getAgeGroup()).isEqualTo(AgeGroup.AGE_6_8);
        assertThat(savedKid.getParent()).isEqualTo(parent);
        assertThat(savedKid.getUser()).isEqualTo(kidUser);
    }

    @Test
    @DisplayName("Kid entity should only have fields appropriate for anonymous children")
    void kidEntityShouldHaveAppropriateFieldsForAnonymousChildren() {
        // Get the metamodel for the Kid entity
        Metamodel metamodel = entityManager.getMetamodel();
        EntityType<Kid> kidEntityType = metamodel.entity(Kid.class);
        
        Set<String> attributeNames = kidEntityType.getDeclaredSingularAttributes().stream()
                .map(attr -> attr.getName())
                .collect(java.util.stream.Collectors.toSet());
        
        // Verify that Kid entity has only appropriate fields for anonymous children
        assertThat(attributeNames).containsExactlyInAnyOrder(
                "id", "nickname", "age", "favoriteColor", "avatarId", 
                "interests", "parent", "user", "ageGroup"
        );
        
        // Verify that there are no personal identification fields
        assertThat(attributeNames).doesNotContain("firstName");
        assertThat(attributeNames).doesNotContain("lastName");
        assertThat(attributeNames).doesNotContain("first_name");
        assertThat(attributeNames).doesNotContain("last_name");
        assertThat(attributeNames).doesNotContain("fullName");
        assertThat(attributeNames).doesNotContain("realName");
        assertThat(attributeNames).doesNotContain("legalName");
    }
} 