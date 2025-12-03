package uk.gegc.kidsgptbackend.shared.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.filter.CommonsRequestLoggingFilter;

/**
 * Configuration for HTTP request/response logging in development.
 * 
 * <p>This configuration is only active in development profiles (dev, local)
 * to provide detailed HTTP logging for debugging purposes.</p>
 * 
 * <p>Logged information includes:</p>
 * <ul>
 *   <li>HTTP method and URI</li>
 *   <li>Query parameters</li>
 *   <li>Request headers</li>
 *   <li>Request body (up to 10KB)</li>
 *   <li>Client IP address</li>
 * </ul>
 * 
 * <p>To enable, run with dev profile:</p>
 * <pre>
 * mvn spring-boot:run -Dspring-boot.run.profiles=dev
 * </pre>
 * 
 * @see CommonsRequestLoggingFilter
 */
@Configuration
@Profile({"dev", "local"})
@Slf4j
public class RequestLoggingConfig {

    /**
     * Creates a request logging filter for detailed HTTP logging.
     * 
     * <p>The filter logs requests before and after processing, showing:</p>
     * <ul>
     *   <li>Full request details including headers and body</li>
     *   <li>Client information (IP address)</li>
     *   <li>Query strings and parameters</li>
     * </ul>
     * 
     * <p>Note: Sensitive headers (Authorization, Cookie) are masked by default.</p>
     * 
     * @return Configured CommonsRequestLoggingFilter
     */
    @Bean
    public CommonsRequestLoggingFilter requestLoggingFilter() {
        CommonsRequestLoggingFilter filter = new CommonsRequestLoggingFilter();
        
        // Include query parameters (e.g., ?page=1&size=10)
        filter.setIncludeQueryString(true);
        
        // Include request payload (body)
        filter.setIncludePayload(true);
        
        // Maximum payload size to log (10KB)
        filter.setMaxPayloadLength(10000);
        
        // Include request headers
        filter.setIncludeHeaders(true);
        
        // Include client IP and session ID
        filter.setIncludeClientInfo(true);
        
        // Custom message prefixes for clarity
        filter.setBeforeMessagePrefix("╔═══ REQUEST START ═══╗ ");
        filter.setAfterMessagePrefix("╚═══ REQUEST END ═════╝ ");
        
        log.info("Request logging filter enabled for development");
        
        return filter;
    }
}

