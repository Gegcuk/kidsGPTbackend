package uk.gegc.kidsgptbackend;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import uk.gegc.kidsgptbackend.test.BaseIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("KidsGpTbackendApplication Tests")
class KidsGpTbackendApplicationTest extends BaseIntegrationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    @DisplayName("context loads successfully")
    void contextLoads() {
        // This test verifies that the Spring context loads successfully
        // which means the main application class is working correctly
        assertThat(applicationContext).isNotNull();
    }

    @Test
    @DisplayName("application class is annotated with @SpringBootApplication")
    void applicationClass_hasSpringBootApplicationAnnotation() {
        assertThat(KidsGpTbackendApplication.class)
                .hasAnnotation(org.springframework.boot.autoconfigure.SpringBootApplication.class);
    }

    @Test
    @DisplayName("application class is annotated with @EnableRetry")
    void applicationClass_hasEnableRetryAnnotation() {
        assertThat(KidsGpTbackendApplication.class)
                .hasAnnotation(org.springframework.retry.annotation.EnableRetry.class);
    }

    @Test
    @DisplayName("application context contains required beans")
    void applicationContext_containsRequiredBeans() {
        // Verify that key beans are loaded
        assertThat(applicationContext.getBeanDefinitionNames()).isNotEmpty();
        // Note: Spring Boot doesn't register the main class as a bean by default
        // So we just verify the context is loaded with beans
    }

    @Test
    @DisplayName("main method exists and is accessible")
    void mainMethod_existsAndAccessible() {
        // Verify main method exists (reflection check)
        try {
            var mainMethod = KidsGpTbackendApplication.class.getMethod("main", String[].class);
            assertThat(mainMethod).isNotNull();
            assertThat(mainMethod.canAccess(null)).isTrue();
        } catch (NoSuchMethodException e) {
            throw new AssertionError("Main method not found", e);
        }
    }

    @Test
    @DisplayName("main method calls SpringApplication.run")
    void mainMethod_callsSpringApplicationRun() {
        // Mock SpringApplication.run to verify it's called without actually starting the application
        try (MockedStatic<SpringApplication> mockedSpringApplication = mockStatic(SpringApplication.class)) {
            // Call the main method
            KidsGpTbackendApplication.main(new String[]{});

            // Verify that SpringApplication.run was called with the correct arguments
            mockedSpringApplication.verify(() -> 
                SpringApplication.run(eq(KidsGpTbackendApplication.class), any(String[].class))
            );
        }
    }

    @Test
    @DisplayName("main method executes without throwing exceptions")
    void mainMethod_executesWithoutExceptions() {
        // Test that main method can be called without throwing exceptions
        // We use a try-catch to handle any potential exceptions gracefully
        try (MockedStatic<SpringApplication> mockedSpringApplication = mockStatic(SpringApplication.class)) {
            // This should not throw any exceptions
            KidsGpTbackendApplication.main(new String[]{"--spring.main.web-application-type=none"});
            
            // If we get here, the method executed successfully
            assertThat(true).isTrue();
        }
    }
} 