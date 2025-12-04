package uk.gegc.kidsgptbackend.features.tips.api;

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
import uk.gegc.kidsgptbackend.features.tips.api.dto.DailyTipDto;
import uk.gegc.kidsgptbackend.features.user.domain.model.AgeGroup;
import uk.gegc.kidsgptbackend.features.tips.application.DailyTipService;
import uk.gegc.kidsgptbackend.features.subscription.application.SubscriptionAccessService;
import uk.gegc.kidsgptbackend.features.user.domain.repository.UserRepository;
import uk.gegc.kidsgptbackend.features.user.domain.model.User;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

@WebMvcTest(controllers = uk.gegc.kidsgptbackend.features.tips.api.TipController.class)
@AutoConfigureMockMvc
@Import({TipControllerTest.TestConfig.class, uk.gegc.kidsgptbackend.shared.config.ClockConfig.class})
@DirtiesContext
class TipControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DailyTipService dailyTipService;

    @MockitoBean
    private SubscriptionAccessService subscriptionAccessService;

    @MockitoBean
    private UserRepository userRepository;

    @TestConfiguration
    static class TestConfig {
        @Bean
        DailyTipService dailyTipService() {
            return Mockito.mock(DailyTipService.class);
        }
    }

    @Test
    @DisplayName("GET /api/v1/tips/daily: returns daily tip")
    void getDailyTip_returnsTip() throws Exception {
        // Given
        DailyTipDto tip = new DailyTipDto();
        tip.setFact("Did you know that honey never spoils?");
        tip.setCategory("science");
        tip.setAgeGroup("AGE_9_10");

        User kid = new User();
        kid.setId(java.util.UUID.randomUUID());
        kid.setUsername("kid");
        when(userRepository.findByUsername("kid")).thenReturn(java.util.Optional.of(kid));
        when(subscriptionAccessService.getRemainingDailyFreeMessagesForSubject(kid, kid.getId())).thenReturn(5);
        when(subscriptionAccessService.hasFeatureAccess(kid, "chat_limit")).thenReturn(true);

        when(dailyTipService.getDailyTip()).thenReturn(tip);

        // When/Then
        mockMvc.perform(get("/api/v1/tips/daily").with(user("kid")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fact").value("Did you know that honey never spoils?"))
                .andExpect(jsonPath("$.category").value("science"))
                .andExpect(jsonPath("$.ageGroup").value("AGE_9_10"));
    }

    @Test
    @DisplayName("GET /api/v1/tips/daily with ageGroup: returns tip for specific age")
    void getDailyTip_withAgeGroup_returnsTipForAge() throws Exception {
        // Given
        DailyTipDto tip = new DailyTipDto();
        tip.setFact("Did you know that honey never spoils?");
        tip.setCategory("science");
        tip.setAgeGroup("AGE_6_8");

        User kid = new User();
        kid.setId(java.util.UUID.randomUUID());
        kid.setUsername("kid");
        when(userRepository.findByUsername("kid")).thenReturn(java.util.Optional.of(kid));
        when(subscriptionAccessService.getRemainingDailyFreeMessagesForSubject(kid, kid.getId())).thenReturn(5);
        when(subscriptionAccessService.hasFeatureAccess(kid, "chat_limit")).thenReturn(true);

        when(dailyTipService.getDailyTip(AgeGroup.AGE_6_8)).thenReturn(tip);

        // When/Then
        mockMvc.perform(get("/api/v1/tips/daily")
                        .param("ageGroup", "AGE_6_8")
                        .with(user("kid")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fact").value("Did you know that honey never spoils?"))
                .andExpect(jsonPath("$.ageGroup").value("AGE_6_8"));
    }
}
