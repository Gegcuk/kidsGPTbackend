package uk.gegc.kidsgptbackend.shared.util;

import org.junit.jupiter.api.Test;
import org.springframework.web.context.request.RequestContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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

    @Test
    void getCurrentRequestContext_WhenExceptionOccurs_ShouldReturnNull() {
        // This tests the exception handling branch
        // When RequestContextHolder.currentRequestAttributes() throws an exception
        // The method should catch it and return null
        try {
            RequestContextHolder.resetRequestAttributes();
        } catch (Exception e) {
            // Ignore if no context exists
        }
        
        RequestContext result = RequestContextUtil.getCurrentRequestContext();
        assertNull(result);
    }

    @Test
    void getServerCapturedIp_WhenContextIsNull_ShouldReturnUnknown() {
        // Test the branch where context is null
        try {
            RequestContextHolder.resetRequestAttributes();
        } catch (Exception e) {
            // Ignore if no context exists
        }
        
        String result = RequestContextUtil.getServerCapturedIp();
        assertEquals("unknown", result);
    }

    @Test
    void getServerCapturedUserAgent_WhenContextIsNull_ShouldReturnUnknown() {
        // Test the branch where context is null
        try {
            RequestContextHolder.resetRequestAttributes();
        } catch (Exception e) {
            // Ignore if no context exists
        }
        
        String result = RequestContextUtil.getServerCapturedUserAgent();
        assertEquals("unknown", result);
    }
} 