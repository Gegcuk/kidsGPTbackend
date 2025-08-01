package uk.gegc.kidsgptbackend.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Utility class to access RequestContext from anywhere in the application.
 */
@Slf4j
public class RequestContextUtil {

    private static final String REQUEST_CONTEXT_ATTRIBUTE = "requestContext";

    /**
     * Get the current RequestContext from RequestContextHolder.
     * 
     * @return RequestContext if available, null otherwise
     */
    public static RequestContext getCurrentRequestContext() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
            return (RequestContext) attributes.getAttribute(REQUEST_CONTEXT_ATTRIBUTE, ServletRequestAttributes.SCOPE_REQUEST);
        } catch (Exception e) {
            log.debug("Could not retrieve RequestContext: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Get the server-captured IP address.
     * 
     * @return server-captured IP address, or "unknown" if not available
     */
    public static String getServerCapturedIp() {
        RequestContext context = getCurrentRequestContext();
        return context != null ? context.getServerCapturedIp() : "unknown";
    }

    /**
     * Get the server-captured User-Agent.
     * 
     * @return server-captured User-Agent, or "unknown" if not available
     */
    public static String getServerCapturedUserAgent() {
        RequestContext context = getCurrentRequestContext();
        return context != null ? context.getServerCapturedUserAgent() : "unknown";
    }
} 