package uk.gegc.kidsgptbackend.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import uk.gegc.kidsgptbackend.util.RequestContext;

import java.io.IOException;

/**
 * Filter to capture server-side request data (IP, User-Agent) and store in RequestContext.
 * This ensures we use server-derived values rather than trusting client-provided data.
 */
@Component("customRequestContextFilter")
@Slf4j
public class RequestContextFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        if (request instanceof HttpServletRequest httpRequest) {
            String serverCapturedIp = extractClientIpAddress(httpRequest);
            String serverCapturedUserAgent = httpRequest.getHeader("User-Agent");
            
            RequestContext requestContext = new RequestContext(serverCapturedIp, serverCapturedUserAgent);
            
            // Store directly in request attributes for safer access
            request.setAttribute("requestContext", requestContext);
            
            log.debug("Captured server-side request data - IP: {}, User-Agent: {}", 
                    serverCapturedIp, serverCapturedUserAgent);
        }
        
        chain.doFilter(request, response);
    }

    /**
     * Extract the real client IP address, handling proxy headers.
     * Priority: X-Forwarded-For > X-Real-IP > RemoteAddr
     */
    private String extractClientIpAddress(HttpServletRequest request) {
        // Check X-Forwarded-For header (most common proxy header)
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty() && !"unknown".equalsIgnoreCase(xForwardedFor)) {
            // X-Forwarded-For can contain multiple IPs, take the first one
            String[] ips = xForwardedFor.split(",");
            String clientIp = ips[0].trim();
            if (!clientIp.isEmpty() && !"unknown".equalsIgnoreCase(clientIp)) {
                return clientIp;
            }
        }
        
        // Check X-Real-IP header
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty() && !"unknown".equalsIgnoreCase(xRealIp)) {
            return xRealIp;
        }
        
        // Fallback to remote address
        String remoteAddr = request.getRemoteAddr();
        if (remoteAddr != null && !remoteAddr.isEmpty()) {
            return remoteAddr;
        }
        
        // Last resort
        return "unknown";
    }
} 