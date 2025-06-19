package uk.gegc.kidsgptbackend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@Execution(ExecutionMode.CONCURRENT)
public class JwtAuthenticationFilterTest {

    @Mock
    HttpServletRequest httpServletRequest;

    @Mock
    HttpServletResponse httpServletResponse;

    @Mock
    FilterChain filterChain;

    @Mock
    JwtTokenProvider jwtTokenProvider;

    @Mock
    uk.gegc.kidsgptbackend.repository.auth.RevokedTokenRepository revokedTokenRepository;

    JwtAuthenticationFilter authenticationFilter;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        authenticationFilter = new JwtAuthenticationFilter(jwtTokenProvider, revokedTokenRepository);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("No authorizetion header -> filterchain invoked, no Authentication set")
    void noHeader_shouldNotSetAuthentication() throws ServletException, IOException {
        when(httpServletRequest.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn(null);

        authenticationFilter.doFilterInternal(httpServletRequest, httpServletResponse, filterChain);

        verify(filterChain).doFilter(httpServletRequest, httpServletResponse);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("Bearer valid-token → filterChain invoked, Authentication set to returned value")
    void validBearer_shouldSetAuthentication() throws ServletException, IOException {
        String token = "valid-token";
        Authentication authentication = mock(Authentication.class);

        when(httpServletRequest.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer " + token);
        when(jwtTokenProvider.validateToken(token)).thenReturn(true);
        when(jwtTokenProvider.getAuthentication(token)).thenReturn(authentication);

        authenticationFilter.doFilterInternal(httpServletRequest, httpServletResponse, filterChain);

        verify(jwtTokenProvider).validateToken(token);
        verify(jwtTokenProvider).getAuthentication(token);
        verify(filterChain).doFilter(httpServletRequest, httpServletResponse);

        assertThat(SecurityContextHolder.getContext().getAuthentication())
                .isSameAs(authentication);
    }

    @Test
    @DisplayName("Bearer invalid-token → filterChain invoked, no Authentication set")
    void invalidBearer_shouldNotSetAuthentication() throws ServletException, IOException {
        String token = "invalid-token";

        when(httpServletRequest.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer " + token);
        when(jwtTokenProvider.validateToken(token)).thenReturn(false);

        authenticationFilter.doFilterInternal(httpServletRequest, httpServletResponse, filterChain);

        verify(jwtTokenProvider).validateToken(token);
        verify(jwtTokenProvider, never()).getAuthentication(anyString());
        verify(filterChain).doFilter(httpServletRequest, httpServletResponse);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("Wrong prefix (‘Token abc’) → filterChain invoked, no Authentication set")
    void wrongPrefix_shouldNotSetAuthentication() throws ServletException, IOException {
        when(httpServletRequest.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Token abc");

        authenticationFilter.doFilterInternal(httpServletRequest, httpServletResponse, filterChain);

        verify(jwtTokenProvider, never()).validateToken(anyString());
        verify(filterChain).doFilter(httpServletRequest, httpServletResponse);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("Bearer revoked-token → filterChain invoked, no Authentication set")
    void revokedToken_shouldNotSetAuthentication() throws ServletException, IOException {
        String token = "revoked-token";

        when(httpServletRequest.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer " + token);
        when(jwtTokenProvider.validateToken(token)).thenReturn(true);
        when(revokedTokenRepository.existsByToken(token)).thenReturn(true);

        authenticationFilter.doFilterInternal(httpServletRequest, httpServletResponse, filterChain);

        verify(jwtTokenProvider).validateToken(token);
        verify(revokedTokenRepository).existsByToken(token);
        verify(jwtTokenProvider, never()).getAuthentication(anyString());
        verify(filterChain).doFilter(httpServletRequest, httpServletResponse);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}