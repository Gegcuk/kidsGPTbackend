package uk.gegc.kidsgptbackend.features.systemstatus.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import uk.gegc.kidsgptbackend.test.BaseIntegrationTest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@DisplayName("EchoController Integration Tests")
class EchoControllerTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("echo: when msg parameter is provided then return message + '1'")
    void echo_whenMsgParameterProvided_thenReturnMessagePlusOne() throws Exception {
        mockMvc.perform(get("/echo").param("msg", "hi"))
                .andExpect(status().isOk())
                .andExpect(content().string("hi1"));
    }

    @Test
    @DisplayName("echo: when msg parameter is missing then return default 'Hello' + '1'")
    void echo_whenMsgParameterMissing_thenReturnDefaultHelloPlusOne() throws Exception {
        mockMvc.perform(get("/echo"))
                .andExpect(status().isOk())
                .andExpect(content().string("Hello1"));
    }

    @Test
    @DisplayName("echo: when msg is empty string then use default 'Hello' and return 'Hello1'")
    void echo_whenMsgIsEmptyString_thenUseDefault() throws Exception {
        // Empty string parameter triggers default value "Hello"
        mockMvc.perform(get("/echo").param("msg", ""))
                .andExpect(status().isOk())
                .andExpect(content().string("Hello1"));
    }

    @Test
    @DisplayName("echo: when msg contains special characters then handle correctly")
    void echo_whenMsgContainsSpecialCharacters_thenHandleCorrectly() throws Exception {
        mockMvc.perform(get("/echo").param("msg", "test@123"))
                .andExpect(status().isOk())
                .andExpect(content().string("test@1231"));
    }

    @Test
    @DisplayName("echo: when msg contains spaces then handle correctly")
    void echo_whenMsgContainsSpaces_thenHandleCorrectly() throws Exception {
        mockMvc.perform(get("/echo").param("msg", "hello world"))
                .andExpect(status().isOk())
                .andExpect(content().string("hello world1"));
    }

    @Test
    @DisplayName("echo: when msg is very long then handle correctly")
    void echo_whenMsgIsVeryLong_thenHandleCorrectly() throws Exception {
        String longMessage = "a".repeat(1000);
        mockMvc.perform(get("/echo").param("msg", longMessage))
                .andExpect(status().isOk())
                .andExpect(content().string(longMessage + "1"));
    }

    @Test
    @DisplayName("echo: when msg contains unicode characters then handle correctly")
    void echo_whenMsgContainsUnicode_thenHandleCorrectly() throws Exception {
        mockMvc.perform(get("/echo").param("msg", "Привет 你好"))
                .andExpect(status().isOk())
                .andExpect(content().string("Привет 你好1"));
    }
}

