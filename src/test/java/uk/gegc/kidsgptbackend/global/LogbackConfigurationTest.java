package uk.gegc.kidsgptbackend.global;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import uk.gegc.kidsgptbackend.config.TestClockConfig;

import java.util.Iterator;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for logback configuration validation:
 * - Logback configuration is properly set up
 * - Appenders are correctly configured
 * - Log levels are appropriate
 * - File rolling policies are configured
 * - Console and file appenders are both present
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@SpringBootTest
@ActiveProfiles("test")
@Import(TestClockConfig.class)
@DisplayName("Logback Configuration Tests")
class LogbackConfigurationTest {

    private LoggerContext loggerContext;

    @BeforeEach
    void setUp() {
        loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
    }

    @Test
    @DisplayName("Root logger should be properly configured")
    void rootLogger_shouldBeProperlyConfigured() {
        // Given & When
        Logger rootLogger = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);

        // Then
        assertThat(rootLogger).isNotNull();
        assertThat(rootLogger.getLevel()).isEqualTo(Level.INFO);
        assertThat(rootLogger.getAppender("CONSOLE")).isNotNull();
    }

    @Test
    @DisplayName("Console appender should be properly configured")
    void consoleAppender_shouldBeProperlyConfigured() {
        // Given & When
        Appender<ILoggingEvent> consoleAppender = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME).getAppender("CONSOLE");

        // Then
        assertThat(consoleAppender).isNotNull();
        assertThat(consoleAppender).isInstanceOf(ConsoleAppender.class);
        assertThat(consoleAppender.isStarted()).isTrue();
    }

    @Test
    @DisplayName("Chat file appender should be properly configured")
    void chatFileAppender_shouldBeProperlyConfigured() {
        // Given & When
        // CHAT_FILE appender is attached to specific loggers, not root logger
        Logger chatControllerLogger = loggerContext.getLogger("uk.gegc.kidsgptbackend.features.chat.api.ChatController");
        Appender<ILoggingEvent> chatFileAppender = chatControllerLogger.getAppender("CHAT_FILE");

        // Then
        assertThat(chatFileAppender).isNotNull();
        assertThat(chatFileAppender).isInstanceOf(RollingFileAppender.class);
        assertThat(chatFileAppender.isStarted()).isTrue();
    }

    @Test
    @DisplayName("Chat file appender should have proper rolling policy")
    void chatFileAppender_shouldHaveProperRollingPolicy() {
        // Given & When
        // CHAT_FILE appender is attached to specific loggers, not root logger
        Logger chatControllerLogger = loggerContext.getLogger("uk.gegc.kidsgptbackend.features.chat.api.ChatController");
        RollingFileAppender<ILoggingEvent> chatFileAppender = 
            (RollingFileAppender<ILoggingEvent>) chatControllerLogger.getAppender("CHAT_FILE");

        // Then
        assertThat(chatFileAppender).isNotNull();
        assertThat(chatFileAppender.getRollingPolicy()).isInstanceOf(SizeAndTimeBasedRollingPolicy.class);
        
        SizeAndTimeBasedRollingPolicy<?> rollingPolicy = 
            (SizeAndTimeBasedRollingPolicy<?>) chatFileAppender.getRollingPolicy();
        
        assertThat(rollingPolicy.getFileNamePattern()).contains("chat-messages.%d{yyyy-MM-dd}.%i.log");
        assertThat(rollingPolicy.getMaxHistory()).isEqualTo(30);
        // Note: getMaxFileSize() and getTotalSizeCap() methods may not be available in all versions
    }

    @Test
    @DisplayName("ChatController logger should be properly configured")
    void chatControllerLogger_shouldBeProperlyConfigured() {
        // Given & When
        Logger chatControllerLogger = loggerContext.getLogger("uk.gegc.kidsgptbackend.features.chat.api.ChatController");

        // Then
        assertThat(chatControllerLogger).isNotNull();
        assertThat(chatControllerLogger.getLevel()).isEqualTo(Level.INFO);
        assertThat(chatControllerLogger.getAppender("CHAT_FILE")).isNotNull();
        assertThat(chatControllerLogger.getAppender("CONSOLE")).isNotNull();
        assertThat(chatControllerLogger.isAdditive()).isFalse(); // additivity should be false
    }

    @Test
    @DisplayName("AiChatServiceImpl logger should be properly configured")
    void aiChatServiceImplLogger_shouldBeProperlyConfigured() {
        // Given & When
        Logger aiChatServiceLogger = loggerContext.getLogger("uk.gegc.kidsgptbackend.features.chat.application.impl.AiChatServiceImpl");

        // Then
        assertThat(aiChatServiceLogger).isNotNull();
        assertThat(aiChatServiceLogger.getLevel()).isEqualTo(Level.INFO);
        assertThat(aiChatServiceLogger.getAppender("CHAT_FILE")).isNotNull();
        assertThat(aiChatServiceLogger.getAppender("CONSOLE")).isNotNull();
        assertThat(aiChatServiceLogger.isAdditive()).isFalse(); // additivity should be false
    }

    @Test
    @DisplayName("ChatMessageServiceImpl logger should be properly configured")
    void chatMessageServiceImplLogger_shouldBeProperlyConfigured() {
        // Given & When
        Logger chatMessageServiceLogger = loggerContext.getLogger("uk.gegc.kidsgptbackend.features.chat.application.impl.ChatMessageServiceImpl");

        // Then
        assertThat(chatMessageServiceLogger).isNotNull();
        assertThat(chatMessageServiceLogger.getLevel()).isEqualTo(Level.INFO);
        assertThat(chatMessageServiceLogger.getAppender("CHAT_FILE")).isNotNull();
        assertThat(chatMessageServiceLogger.getAppender("CONSOLE")).isNotNull();
        assertThat(chatMessageServiceLogger.isAdditive()).isFalse(); // additivity should be false
    }

    @Test
    @DisplayName("All appenders should be started")
    void allAppenders_shouldBeStarted() {
        // Given & When
        Logger rootLogger = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);
        Iterator<Appender<ILoggingEvent>> appenderIterator = rootLogger.iteratorForAppenders();

        // Then
        while (appenderIterator.hasNext()) {
            Appender<ILoggingEvent> appender = appenderIterator.next();
            assertThat(appender.isStarted()).isTrue();
        }
    }

    @Test
    @DisplayName("Logger context should be properly initialized")
    void loggerContext_shouldBeProperlyInitialized() {
        // Given & When
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();

        // Then
        assertThat(context).isNotNull();
        assertThat(context.getName()).isNotNull();
        assertThat(context.getStatusManager()).isNotNull();
    }

    @Test
    @DisplayName("Log levels should be appropriate for production use")
    void logLevels_shouldBeAppropriateForProductionUse() {
        // Given & When
        Logger rootLogger = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);
        Logger chatControllerLogger = loggerContext.getLogger("uk.gegc.kidsgptbackend.features.chat.api.ChatController");
        Logger aiChatServiceLogger = loggerContext.getLogger("uk.gegc.kidsgptbackend.features.chat.application.impl.AiChatServiceImpl");
        Logger chatMessageServiceLogger = loggerContext.getLogger("uk.gegc.kidsgptbackend.features.chat.application.impl.ChatMessageServiceImpl");

        // Then
        // Root logger should be at INFO level (not DEBUG to avoid excessive logging)
        assertThat(rootLogger.getLevel()).isEqualTo(Level.INFO);
        
        // Chat-related loggers should be at INFO level for proper monitoring
        assertThat(chatControllerLogger.getLevel()).isEqualTo(Level.INFO);
        assertThat(aiChatServiceLogger.getLevel()).isEqualTo(Level.INFO);
        assertThat(chatMessageServiceLogger.getLevel()).isEqualTo(Level.INFO);
    }

    @Test
    @DisplayName("File appender should have proper file path configuration")
    void fileAppender_shouldHaveProperFilePathConfiguration() {
        // Given & When
        // CHAT_FILE appender is attached to specific loggers, not root logger
        Logger chatControllerLogger = loggerContext.getLogger("uk.gegc.kidsgptbackend.features.chat.api.ChatController");
        RollingFileAppender<ILoggingEvent> chatFileAppender = 
            (RollingFileAppender<ILoggingEvent>) chatControllerLogger.getAppender("CHAT_FILE");

        // Then
        assertThat(chatFileAppender).isNotNull();
        assertThat(chatFileAppender.getFile()).isEqualTo("logs/chat-messages.log");
    }

    @Test
    @DisplayName("Console appender should have proper pattern configuration")
    void consoleAppender_shouldHaveProperPatternConfiguration() {
        // Given & When
        ConsoleAppender<ILoggingEvent> consoleAppender = 
            (ConsoleAppender<ILoggingEvent>) loggerContext.getLogger(Logger.ROOT_LOGGER_NAME).getAppender("CONSOLE");

        // Then
        assertThat(consoleAppender).isNotNull();
        assertThat(consoleAppender.getEncoder()).isNotNull();
    }

    @Test
    @DisplayName("Chat file appender should have proper pattern configuration")
    void chatFileAppender_shouldHaveProperPatternConfiguration() {
        // Given & When
        // CHAT_FILE appender is attached to specific loggers, not root logger
        Logger chatControllerLogger = loggerContext.getLogger("uk.gegc.kidsgptbackend.features.chat.api.ChatController");
        RollingFileAppender<ILoggingEvent> chatFileAppender = 
            (RollingFileAppender<ILoggingEvent>) chatControllerLogger.getAppender("CHAT_FILE");

        // Then
        assertThat(chatFileAppender).isNotNull();
        assertThat(chatFileAppender.getEncoder()).isNotNull();
    }

    @Test
    @DisplayName("Logger configuration should support proper log rotation")
    void loggerConfiguration_shouldSupportProperLogRotation() {
        // Given & When
        // CHAT_FILE appender is attached to specific loggers, not root logger
        Logger chatControllerLogger = loggerContext.getLogger("uk.gegc.kidsgptbackend.features.chat.api.ChatController");
        RollingFileAppender<ILoggingEvent> chatFileAppender = 
            (RollingFileAppender<ILoggingEvent>) chatControllerLogger.getAppender("CHAT_FILE");

        // Then
        assertThat(chatFileAppender).isNotNull();
        assertThat(chatFileAppender.getRollingPolicy()).isNotNull();
        
        SizeAndTimeBasedRollingPolicy<?> rollingPolicy = 
            (SizeAndTimeBasedRollingPolicy<?>) chatFileAppender.getRollingPolicy();
        
        // Should have reasonable rotation settings
        assertThat(rollingPolicy.getMaxHistory()).isGreaterThan(0);
        // Note: getMaxFileSize() and getTotalSizeCap() methods may not be available in all versions
    }

    @Test
    @DisplayName("Logger configuration should be consistent across chat-related loggers")
    void loggerConfiguration_shouldBeConsistentAcrossChatRelatedLoggers() {
        // Given & When
        Logger chatControllerLogger = loggerContext.getLogger("uk.gegc.kidsgptbackend.features.chat.api.ChatController");
        Logger aiChatServiceLogger = loggerContext.getLogger("uk.gegc.kidsgptbackend.features.chat.application.impl.AiChatServiceImpl");
        Logger chatMessageServiceLogger = loggerContext.getLogger("uk.gegc.kidsgptbackend.features.chat.application.impl.ChatMessageServiceImpl");

        // Then
        // All chat-related loggers should have the same configuration
        assertThat(chatControllerLogger.getLevel()).isEqualTo(aiChatServiceLogger.getLevel());
        assertThat(aiChatServiceLogger.getLevel()).isEqualTo(chatMessageServiceLogger.getLevel());
        
        assertThat(chatControllerLogger.isAdditive()).isEqualTo(aiChatServiceLogger.isAdditive());
        assertThat(aiChatServiceLogger.isAdditive()).isEqualTo(chatMessageServiceLogger.isAdditive());
        
        // All should have both CONSOLE and CHAT_FILE appenders
        assertThat(chatControllerLogger.getAppender("CONSOLE")).isNotNull();
        assertThat(chatControllerLogger.getAppender("CHAT_FILE")).isNotNull();
        
        assertThat(aiChatServiceLogger.getAppender("CONSOLE")).isNotNull();
        assertThat(aiChatServiceLogger.getAppender("CHAT_FILE")).isNotNull();
        
        assertThat(chatMessageServiceLogger.getAppender("CONSOLE")).isNotNull();
        assertThat(chatMessageServiceLogger.getAppender("CHAT_FILE")).isNotNull();
    }
}
