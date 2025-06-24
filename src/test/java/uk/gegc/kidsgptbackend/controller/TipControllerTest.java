package uk.gegc.kidsgptbackend.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import uk.gegc.kidsgptbackend.dto.tips.DailyTipDto;
import uk.gegc.kidsgptbackend.service.tips.DailyTipService;
import uk.gegc.kidsgptbackend.model.user.AgeGroup;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class TipControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DailyTipService dailyTipService;

    @Test
    @DisplayName("GET /api/v1/tips/daily: returns daily tip")
    void getDailyTip_returnsTip() throws Exception {
        // Given
        DailyTipDto tip = new DailyTipDto();
        tip.setFact("Did you know that honey never spoils?");
        tip.setCategory("science");
        tip.setAgeGroup("AGE_9_10");
        
        when(dailyTipService.getDailyTip()).thenReturn(tip);

        // When/Then
        mockMvc.perform(get("/api/v1/tips/daily"))
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
        
        when(dailyTipService.getDailyTip(AgeGroup.AGE_6_8)).thenReturn(tip);

        // When/Then
        mockMvc.perform(get("/api/v1/tips/daily")
                        .param("ageGroup", "AGE_6_8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fact").value("Did you know that honey never spoils?"))
                .andExpect(jsonPath("$.ageGroup").value("AGE_6_8"));
    }
} 