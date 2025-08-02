package uk.gegc.kidsgptbackend.util;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.jupiter.api.Assertions.*;

class RequestContextUtilTest {

    @Test
    void getServerCapturedIp_WhenNoRequestContext_ShouldReturnUnknown() {
        // Clear any existing request context
        try {
            RequestContextHolder.resetRequestAttributes();
        } catch (Exception e) {
            // Ignore if no context exists
        }
        
        String result = RequestContextUtil.getServerCapturedIp();
        assertEquals("unknown", result);
    }

    @Test
    void getServerCapturedUserAgent_WhenNoRequestContext_ShouldReturnUnknown() {
        // Clear any existing request context
        try {
            RequestContextHolder.resetRequestAttributes();
        } catch (Exception e) {
            // Ignore if no context exists
        }
        
        String result = RequestContextUtil.getServerCapturedUserAgent();
        assertEquals("unknown", result);
    }

    @Test
    void getCurrentRequestContext_WhenNoRequestContext_ShouldReturnNull() {
        // Clear any existing request context
        try {
            RequestContextHolder.resetRequestAttributes();
        } catch (Exception e) {
            // Ignore if no context exists
        }
        
        RequestContext result = RequestContextUtil.getCurrentRequestContext();
        assertNull(result);
    }
} 