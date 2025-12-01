package uk.gegc.kidsgptbackend.shared.util;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

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


    @Test
    void getCurrentRequestContext_WhenRequestHasContextAttribute_ShouldReturnContext() {
        // Test the happy path where request has the context attribute
        try {
            RequestContextHolder.resetRequestAttributes();
        } catch (Exception e) {
            // Ignore if no context exists
        }
        
        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContext context = new RequestContext("192.168.1.1", "Mozilla/5.0");
        request.setAttribute("requestContext", context);
        
        ServletRequestAttributes attributes = new ServletRequestAttributes(request);
        RequestContextHolder.setRequestAttributes(attributes, true);
        
        RequestContext result = RequestContextUtil.getCurrentRequestContext();
        assertEquals(context, result);
        assertEquals("192.168.1.1", result.getServerCapturedIp());
        assertEquals("Mozilla/5.0", result.getServerCapturedUserAgent());
        
        // Cleanup
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void getServerCapturedIp_WhenContextExists_ShouldReturnIp() {
        // Test the branch where context != null
        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContext context = new RequestContext("10.0.0.1", "Chrome/1.0");
        request.setAttribute("requestContext", context);
        
        ServletRequestAttributes attributes = new ServletRequestAttributes(request);
        RequestContextHolder.setRequestAttributes(attributes, true);
        
        String result = RequestContextUtil.getServerCapturedIp();
        assertEquals("10.0.0.1", result);
        
        // Cleanup
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void getServerCapturedUserAgent_WhenContextExists_ShouldReturnUserAgent() {
        // Test the branch where context != null
        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContext context = new RequestContext("10.0.0.1", "Firefox/2.0");
        request.setAttribute("requestContext", context);
        
        ServletRequestAttributes attributes = new ServletRequestAttributes(request);
        RequestContextHolder.setRequestAttributes(attributes, true);
        
        String result = RequestContextUtil.getServerCapturedUserAgent();
        assertEquals("Firefox/2.0", result);
        
        // Cleanup
        RequestContextHolder.resetRequestAttributes();
    }
} 