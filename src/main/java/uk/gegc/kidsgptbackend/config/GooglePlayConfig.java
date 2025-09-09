package uk.gegc.kidsgptbackend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "google.play")
@Data
public class GooglePlayConfig {
    
    /**
     * Service account key JSON content for Google Play API authentication
     */
    private String serviceAccountKey;
    
    /**
     * Android package name for the app
     */
    private String packageName = "uk.gegc.kidsgpt";
    
    /**
     * Application name for Google Play API requests
     */
    private String applicationName = "KidsGPT";
    
    /**
     * Path to the service account credentials file (alternative to serviceAccountKey)
     */
    private String credentialsFile;
    
    /**
     * Whether to enable Google Play API integration
     */
    private boolean enabled = true;
    
    /**
     * Timeout for API requests in milliseconds
     */
    private long requestTimeoutMs = 30000;
    
    /**
     * Number of retry attempts for failed API calls
     */
    private int maxRetries = 3;
}
