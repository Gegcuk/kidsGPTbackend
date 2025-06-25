package uk.gegc.kidsgptbackend.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureWebMvc
@ActiveProfiles("test")
public class RateLimitIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @Test
    @DisplayName("Should apply rate limiting to auth endpoints")
    void shouldApplyRateLimitingToAuthEndpoints() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        // First 5 requests should succeed (default auth rate limit)
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"usernameOrEmail\":\"test\",\"password\":\"test\"}"))
                    .andExpect(status().isUnauthorized()) // Expected since credentials are invalid
                    .andExpect(header().exists("X-Rate-Limit-Remaining"));
        }

        // 6th request should be rate limited
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"usernameOrEmail\":\"test\",\"password\":\"test\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("X-Rate-Limit-Retry-After-Seconds"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Rate limit exceeded")));
    }

    @Test
    @DisplayName("Should not apply rate limiting to health check endpoints")
    void shouldNotApplyRateLimitingToHealthEndpoints() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        // Multiple requests to health endpoint should not be rate limited
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/api/v1/health"))
                    .andExpect(status().isNotFound()) // Expected since it's a GET endpoint
                    .andExpect(header().doesNotExist("X-Rate-Limit-Remaining"));
        }
    }
} 