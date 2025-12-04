package uk.gegc.kidsgptbackend.features.jokes.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import uk.gegc.kidsgptbackend.features.jokes.api.dto.DailyJokeDto;
import uk.gegc.kidsgptbackend.features.user.domain.model.AgeGroup;
import uk.gegc.kidsgptbackend.features.jokes.application.DailyJokeService;
import uk.gegc.kidsgptbackend.features.subscription.application.SubscriptionAccessService;
import uk.gegc.kidsgptbackend.features.user.domain.repository.UserRepository;
import uk.gegc.kidsgptbackend.features.user.domain.model.User;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

@WebMvcTest(controllers = uk.gegc.kidsgptbackend.features.jokes.api.JokeController.class)
@AutoConfigureMockMvc
@Import({JokeControllerTest.TestConfig.class, uk.gegc.kidsgptbackend.shared.config.ClockConfig.class})
@DirtiesContext
class JokeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DailyJokeService dailyJokeService;

    @MockitoBean
    private SubscriptionAccessService subscriptionAccessService;

    @MockitoBean
    private UserRepository userRepository;

    @TestConfiguration
    static class TestConfig {
        @Bean
        DailyJokeService dailyJokeService() {
            return Mockito.mock(DailyJokeService.class);
        }
    }

    @Test
    @DisplayName("GET /api/v1/jokes/daily: returns daily joke")
    void getDailyJoke_returnsJoke() throws Exception {
        // Given
        DailyJokeDto joke = new DailyJokeDto();
        joke.setJoke("Why don't elephants use computers? Because they're afraid of the mouse!");
        joke.setCategory("animals");
        joke.setAgeGroup("AGE_9_10");

        User kid = new User();
        kid.setId(java.util.UUID.randomUUID());
        kid.setUsername("kid");
        when(userRepository.findByUsername("kid")).thenReturn(java.util.Optional.of(kid));
        when(subscriptionAccessService.getRemainingDailyFreeMessagesForSubject(kid, kid.getId())).thenReturn(5);
        when(subscriptionAccessService.hasFeatureAccess(kid, "chat_limit")).thenReturn(true);

        when(dailyJokeService.getDailyJoke()).thenReturn(joke);

        // When/Then
        mockMvc.perform(get("/api/v1/jokes/daily").with(user("kid")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.joke").value("Why don't elephants use computers? Because they're afraid of the mouse!"))
                .andExpect(jsonPath("$.category").value("animals"))
                .andExpect(jsonPath("$.ageGroup").value("AGE_9_10"));
    }

    @Test
    @DisplayName("GET /api/v1/jokes/daily with ageGroup: returns joke for specific age")
    void getDailyJoke_withAgeGroup_returnsJokeForAge() throws Exception {
        // Given
        DailyJokeDto joke = new DailyJokeDto();
        joke.setJoke("What do you call a sleeping bull? A bulldozer!");
        joke.setCategory("animals");
        joke.setAgeGroup("AGE_6_8");

        User kid = new User();
        kid.setId(java.util.UUID.randomUUID());
        kid.setUsername("kid");
        when(userRepository.findByUsername("kid")).thenReturn(java.util.Optional.of(kid));
        when(subscriptionAccessService.getRemainingDailyFreeMessagesForSubject(kid, kid.getId())).thenReturn(5);
        when(subscriptionAccessService.hasFeatureAccess(kid, "chat_limit")).thenReturn(true);

        when(dailyJokeService.getDailyJoke(AgeGroup.AGE_6_8)).thenReturn(joke);

        // When/Then
        mockMvc.perform(get("/api/v1/jokes/daily")
                        .param("ageGroup", "AGE_6_8")
                        .with(user("kid")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.joke").value("What do you call a sleeping bull? A bulldozer!"))
                .andExpect(jsonPath("$.ageGroup").value("AGE_6_8"));
    }

    @Test
    @DisplayName("GET /api/v1/jokes/daily with invalid ageGroup: returns default joke")
    void getDailyJoke_withInvalidAgeGroup_returnsDefaultJoke() throws Exception {
        // Given
        DailyJokeDto joke = new DailyJokeDto();
        joke.setJoke("Why don't scientists trust atoms? Because they make up everything!");
        joke.setCategory("science");
        joke.setAgeGroup("AGE_9_10");

        User kid = new User();
        kid.setId(java.util.UUID.randomUUID());
        kid.setUsername("kid");
        when(userRepository.findByUsername("kid")).thenReturn(java.util.Optional.of(kid));
        when(subscriptionAccessService.getRemainingDailyFreeMessagesForSubject(kid, kid.getId())).thenReturn(5);
        when(subscriptionAccessService.hasFeatureAccess(kid, "chat_limit")).thenReturn(true);

        when(dailyJokeService.getDailyJoke()).thenReturn(joke);

        // When/Then
        mockMvc.perform(get("/api/v1/jokes/daily")
                        .param("ageGroup", "INVALID_AGE")
                        .with(user("kid")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.joke").value("Why don't scientists trust atoms? Because they make up everything!"))
                .andExpect(jsonPath("$.category").value("science"))
                .andExpect(jsonPath("$.ageGroup").value("AGE_9_10"));
    }
} 
