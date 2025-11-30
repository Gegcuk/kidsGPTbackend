package uk.gegc.kidsgptbackend.shared.util;

import lombok.Data;

/**
 * Context holder for server-captured request data.
 * This ensures we use server-derived values rather than trusting client-provided data.
 */
@Data
public class RequestContext {
    private String serverCapturedIp;
    private String serverCapturedUserAgent;
    
    public RequestContext(String serverCapturedIp, String serverCapturedUserAgent) {
        this.serverCapturedIp = serverCapturedIp;
        this.serverCapturedUserAgent = serverCapturedUserAgent;
    }
} 