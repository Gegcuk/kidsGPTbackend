package uk.gegc.kidsgptbackend.shared.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gegc.kidsgptbackend.shared.util.RequestContext;
import uk.gegc.kidsgptbackend.test.BaseUnitTest;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RequestContextFilter Tests")
class RequestContextFilterTest extends BaseUnitTest {

    @Mock
    private HttpServletRequest httpRequest;

    @Mock
    private ServletRequest servletRequest;

    @Mock
    private ServletResponse servletResponse;

    @Mock
    private FilterChain filterChain;

    private RequestContextFilter filter;

    @Override
    @BeforeEach
    protected void setUp() {
        super.setUp();
        filter = new RequestContextFilter();
    }

    @Test
    @DisplayName("doFilter: when HttpServletRequest then extracts and stores RequestContext")
    void doFilter_httpServletRequest_extractsAndStoresContext() throws Exception {
        // Given
        when(httpRequest.getHeader("User-Agent")).thenReturn("TestAgent/1.0");
        when(httpRequest.getHeader("X-Forwarded-For")).thenReturn("192.168.1.1");
        when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");

        // When
        filter.doFilter(httpRequest, servletResponse, filterChain);

        // Then
        verify(httpRequest).setAttribute(eq("requestContext"), any(RequestContext.class));
        verify(filterChain).doFilter(httpRequest, servletResponse);
    }

    @Test
    @DisplayName("doFilter: when non-HttpServletRequest then continues chain without storing context")
    void doFilter_nonHttpServletRequest_continuesChain() throws Exception {
        // When
        filter.doFilter(servletRequest, servletResponse, filterChain);

        // Then
        verify(servletRequest, never()).setAttribute(anyString(), any());
        verify(filterChain).doFilter(servletRequest, servletResponse);
    }

    @Test
    @DisplayName("doFilter: extracts IP from X-Forwarded-For header")
    void doFilter_xForwardedForHeader_usesFirstIp() throws Exception {
        // Given
        when(httpRequest.getHeader("User-Agent")).thenReturn("TestAgent/1.0");
        when(httpRequest.getHeader("X-Forwarded-For")).thenReturn("192.168.1.1, 10.0.0.1");
        when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");

        // When
        filter.doFilter(httpRequest, servletResponse, filterChain);

        // Then
        verify(httpRequest).setAttribute(eq("requestContext"), any(RequestContext.class));
    }

    @Test
    @DisplayName("doFilter: when X-Forwarded-For is null then uses X-Real-IP")
    void doFilter_xForwardedForNull_usesXRealIp() throws Exception {
        // Given
        when(httpRequest.getHeader("User-Agent")).thenReturn("TestAgent/1.0");
        when(httpRequest.getHeader("X-Forwarded-For")).thenReturn(null);
        when(httpRequest.getHeader("X-Real-IP")).thenReturn("192.168.1.2");
        when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");

        // When
        filter.doFilter(httpRequest, servletResponse, filterChain);

        // Then
        verify(httpRequest).setAttribute(eq("requestContext"), any(RequestContext.class));
    }

    @Test
    @DisplayName("doFilter: when X-Forwarded-For is empty then uses X-Real-IP")
    void doFilter_xForwardedForEmpty_usesXRealIp() throws Exception {
        // Given
        when(httpRequest.getHeader("User-Agent")).thenReturn("TestAgent/1.0");
        when(httpRequest.getHeader("X-Forwarded-For")).thenReturn("");
        when(httpRequest.getHeader("X-Real-IP")).thenReturn("192.168.1.2");
        when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");

        // When
        filter.doFilter(httpRequest, servletResponse, filterChain);

        // Then
        verify(httpRequest).setAttribute(eq("requestContext"), any(RequestContext.class));
    }

    @Test
    @DisplayName("doFilter: when X-Forwarded-For is 'unknown' then uses X-Real-IP")
    void doFilter_xForwardedForUnknown_usesXRealIp() throws Exception {
        // Given
        when(httpRequest.getHeader("User-Agent")).thenReturn("TestAgent/1.0");
        when(httpRequest.getHeader("X-Forwarded-For")).thenReturn("unknown");
        when(httpRequest.getHeader("X-Real-IP")).thenReturn("192.168.1.2");
        when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");

        // When
        filter.doFilter(httpRequest, servletResponse, filterChain);

        // Then
        verify(httpRequest).setAttribute(eq("requestContext"), any(RequestContext.class));
    }

    @Test
    @DisplayName("doFilter: when X-Forwarded-For first IP is empty then uses X-Real-IP")
    void doFilter_xForwardedForFirstIpEmpty_usesXRealIp() throws Exception {
        // Given
        when(httpRequest.getHeader("User-Agent")).thenReturn("TestAgent/1.0");
        when(httpRequest.getHeader("X-Forwarded-For")).thenReturn(" , 192.168.1.1");
        when(httpRequest.getHeader("X-Real-IP")).thenReturn("192.168.1.2");
        when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");

        // When
        filter.doFilter(httpRequest, servletResponse, filterChain);

        // Then
        verify(httpRequest).setAttribute(eq("requestContext"), any(RequestContext.class));
    }

    @Test
    @DisplayName("doFilter: when X-Forwarded-For first IP is 'unknown' then uses X-Real-IP")
    void doFilter_xForwardedForFirstIpUnknown_usesXRealIp() throws Exception {
        // Given
        when(httpRequest.getHeader("User-Agent")).thenReturn("TestAgent/1.0");
        when(httpRequest.getHeader("X-Forwarded-For")).thenReturn("unknown, 192.168.1.1");
        when(httpRequest.getHeader("X-Real-IP")).thenReturn("192.168.1.2");
        when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");

        // When
        filter.doFilter(httpRequest, servletResponse, filterChain);

        // Then
        verify(httpRequest).setAttribute(eq("requestContext"), any(RequestContext.class));
    }

    @Test
    @DisplayName("doFilter: when X-Real-IP is null then uses RemoteAddr")
    void doFilter_xRealIpNull_usesRemoteAddr() throws Exception {
        // Given
        when(httpRequest.getHeader("User-Agent")).thenReturn("TestAgent/1.0");
        when(httpRequest.getHeader("X-Forwarded-For")).thenReturn(null);
        when(httpRequest.getHeader("X-Real-IP")).thenReturn(null);
        when(httpRequest.getRemoteAddr()).thenReturn("192.168.1.3");

        // When
        filter.doFilter(httpRequest, servletResponse, filterChain);

        // Then
        verify(httpRequest).setAttribute(eq("requestContext"), any(RequestContext.class));
    }

    @Test
    @DisplayName("doFilter: when X-Real-IP is empty then uses RemoteAddr")
    void doFilter_xRealIpEmpty_usesRemoteAddr() throws Exception {
        // Given
        when(httpRequest.getHeader("User-Agent")).thenReturn("TestAgent/1.0");
        when(httpRequest.getHeader("X-Forwarded-For")).thenReturn(null);
        when(httpRequest.getHeader("X-Real-IP")).thenReturn("");
        when(httpRequest.getRemoteAddr()).thenReturn("192.168.1.3");

        // When
        filter.doFilter(httpRequest, servletResponse, filterChain);

        // Then
        verify(httpRequest).setAttribute(eq("requestContext"), any(RequestContext.class));
    }

    @Test
    @DisplayName("doFilter: when X-Real-IP is 'unknown' then uses RemoteAddr")
    void doFilter_xRealIpUnknown_usesRemoteAddr() throws Exception {
        // Given
        when(httpRequest.getHeader("User-Agent")).thenReturn("TestAgent/1.0");
        when(httpRequest.getHeader("X-Forwarded-For")).thenReturn(null);
        when(httpRequest.getHeader("X-Real-IP")).thenReturn("unknown");
        when(httpRequest.getRemoteAddr()).thenReturn("192.168.1.3");

        // When
        filter.doFilter(httpRequest, servletResponse, filterChain);

        // Then
        verify(httpRequest).setAttribute(eq("requestContext"), any(RequestContext.class));
    }

    @Test
    @DisplayName("doFilter: when RemoteAddr is null then uses 'unknown'")
    void doFilter_remoteAddrNull_usesUnknown() throws Exception {
        // Given
        when(httpRequest.getHeader("User-Agent")).thenReturn("TestAgent/1.0");
        when(httpRequest.getHeader("X-Forwarded-For")).thenReturn(null);
        when(httpRequest.getHeader("X-Real-IP")).thenReturn(null);
        when(httpRequest.getRemoteAddr()).thenReturn(null);

        // When
        filter.doFilter(httpRequest, servletResponse, filterChain);

        // Then
        verify(httpRequest).setAttribute(eq("requestContext"), any(RequestContext.class));
    }

    @Test
    @DisplayName("doFilter: when RemoteAddr is empty then uses 'unknown'")
    void doFilter_remoteAddrEmpty_usesUnknown() throws Exception {
        // Given
        when(httpRequest.getHeader("User-Agent")).thenReturn("TestAgent/1.0");
        when(httpRequest.getHeader("X-Forwarded-For")).thenReturn(null);
        when(httpRequest.getHeader("X-Real-IP")).thenReturn(null);
        when(httpRequest.getRemoteAddr()).thenReturn("");

        // When
        filter.doFilter(httpRequest, servletResponse, filterChain);

        // Then
        verify(httpRequest).setAttribute(eq("requestContext"), any(RequestContext.class));
    }

    @Test
    @DisplayName("doFilter: when User-Agent is null then handles gracefully")
    void doFilter_userAgentNull_handlesGracefully() throws Exception {
        // Given
        when(httpRequest.getHeader("User-Agent")).thenReturn(null);
        when(httpRequest.getHeader("X-Forwarded-For")).thenReturn("192.168.1.1");
        when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");

        // When
        filter.doFilter(httpRequest, servletResponse, filterChain);

        // Then
        verify(httpRequest).setAttribute(eq("requestContext"), any(RequestContext.class));
        verify(filterChain).doFilter(httpRequest, servletResponse);
    }

    @Test
    @DisplayName("doFilter: when FilterChain throws exception then propagates")
    void doFilter_filterChainThrowsException_propagates() throws Exception {
        // Given
        when(httpRequest.getHeader("User-Agent")).thenReturn("TestAgent/1.0");
        when(httpRequest.getHeader("X-Forwarded-For")).thenReturn("192.168.1.1");
        when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        doThrow(new ServletException("Filter chain error")).when(filterChain).doFilter(any(), any());

        // When/Then
        try {
            filter.doFilter(httpRequest, servletResponse, filterChain);
        } catch (ServletException e) {
            assertThat(e.getMessage()).contains("Filter chain error");
        }
    }

    @Test
    @DisplayName("doFilter: when FilterChain throws IOException then propagates")
    void doFilter_filterChainThrowsIOException_propagates() throws Exception {
        // Given
        when(httpRequest.getHeader("User-Agent")).thenReturn("TestAgent/1.0");
        when(httpRequest.getHeader("X-Forwarded-For")).thenReturn("192.168.1.1");
        when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        doThrow(new IOException("IO error")).when(filterChain).doFilter(any(), any());

        // When/Then
        try {
            filter.doFilter(httpRequest, servletResponse, filterChain);
        } catch (IOException e) {
            assertThat(e.getMessage()).contains("IO error");
        }
    }
}

