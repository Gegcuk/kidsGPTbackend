package uk.gegc.kidsgptbackend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Execution(ExecutionMode.CONCURRENT)
public class JwtTokenProviderTest {

    private final long accessTokenValidityInMs = 15 * 60 * 1000;
    private final long refreshTokenValidityInMs = 7 * 24 * 60 * 60 * 1000;
    private JwtTokenProvider jwtTokenProvider;
    private SecretKey secretKey;
    private String base64Secret;

    @BeforeEach
    void setUp() {
        secretKey = Jwts.SIG.HS256.key().build();
        base64Secret = Base64.getEncoder().encodeToString(secretKey.getEncoded());

        jwtTokenProvider = new JwtTokenProvider(null, null);
        ReflectionTestUtils.setField(jwtTokenProvider, "base64secret", base64Secret);
        ReflectionTestUtils.setField(jwtTokenProvider, "accessTokenValidityInMs", accessTokenValidityInMs);
        ReflectionTestUtils.setField(jwtTokenProvider, "refreshTokenValidityInMs", refreshTokenValidityInMs);

        jwtTokenProvider.init();
    }

    @Test
    @DisplayName("generateAccessToken: valid Authentication produces JWT with type=access, correct subject & TTL")
    void generateAccessToken_happyPath_containsAccessTypeAndSubjectAndExpiry() {
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                "Alice", null, List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );

        String token = jwtTokenProvider.generateAccessToken(authentication);
        assertThat(token).isNotBlank();

        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        assertThat(claims.getSubject()).isEqualTo("Alice");
        assertThat(claims.get("type", String.class)).isEqualTo("access");

        Date issuedAt = claims.getIssuedAt();
        Date expirationDate = claims.getExpiration();

        assertThat(expirationDate.getTime() - issuedAt.getTime()).isEqualTo(accessTokenValidityInMs);
        assertThat(issuedAt).isBeforeOrEqualTo(new Date());
    }

    @Test
    @DisplayName("generateAccessToken: two quick calls yield different issuedAt & expiration timestamps")
    void generateAccessToken_edge_twoCalls_differentIssuedAtAndExpiry() throws InterruptedException {
        Authentication authentication = new UsernamePasswordAuthenticationToken("Bob", null, List.of());

        String token1 = jwtTokenProvider.generateAccessToken(authentication);
        Thread.sleep(1000);
        String token2 = jwtTokenProvider.generateAccessToken(authentication);

        assertThat(token1).isNotEqualTo(token2);

        Claims claims1 = Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token1).getPayload();
        Claims claims2 = Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token2).getPayload();

        assertThat(claims2.getIssuedAt()).isAfter(claims1.getIssuedAt());
        assertThat(claims2.getExpiration()).isAfter(claims1.getExpiration());
    }

    @Test
    @DisplayName("generateRefreshToken: valid Authentication produces JWT with type=refresh, correct subject & TTL")
    void generateRefreshToken_happyPath_containsRefreshTypeAndSubjectAndExpiry() {
        Authentication authentication = new UsernamePasswordAuthenticationToken("Carol", null, List.of());

        String token = jwtTokenProvider.generateRefreshToken(authentication);
        assertThat(token).isNotBlank();

        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        assertThat(claims.getSubject()).isEqualTo("Carol");
        assertThat(claims.get("type")).isEqualTo("refresh");

        Date issuedAt = claims.getIssuedAt();
        Date expiration = claims.getExpiration();
        assertThat(expiration.getTime() - issuedAt.getTime()).isEqualTo(refreshTokenValidityInMs);
        assertThat(issuedAt).isBeforeOrEqualTo(new Date());
    }

    @Test
    @DisplayName("validateToken: valid access token returns true")
    void validateToken_happyPath_validTokenReturnsTrue() {
        Authentication authentication = new UsernamePasswordAuthenticationToken("John", null, List.of());
        String validToken = jwtTokenProvider.generateAccessToken(authentication);

        boolean result = jwtTokenProvider.validateToken(validToken);
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("validateToken: malformed token returns false")
    void validateToken_sad_malformedTokenReturnsFalse() {
        String badToken = "not.a.token";
        assertThat(jwtTokenProvider.validateToken(badToken)).isFalse();
    }

    @Test
    @DisplayName("validateToken: expired token returns false")
    void validateToken_sad_expiredTokenReturnsFalse() {
        Date past = new Date(System.currentTimeMillis() - 1000);
        String expired = Jwts.builder()
                .subject("Eve")
                .issuedAt(past)
                .expiration(past)
                .claim("type", "access")
                .signWith(secretKey)
                .compact();

        assertThat(jwtTokenProvider.validateToken(expired)).isFalse();
    }

    @Test
    @DisplayName("validateToken: token signed with wrong key returns false")
    void validateToken_sad_wrongSignatureReturnsFalse() {
        SecretKey wrongKey = Jwts.SIG.HS256.key().build();
        Date now = new Date();
        String badSignature = Jwts.builder()
                .subject("Frank")
                .issuedAt(now)
                .expiration(new Date(now.getTime() + accessTokenValidityInMs))
                .claim("type", "access")
                .signWith(wrongKey)
                .compact();

        assertThat(jwtTokenProvider.validateToken(badSignature)).isFalse();
    }

    @Test
    @DisplayName("getUsername: valid token returns correct subject")
    void getUsername_happyPath_returnsCorrectUsername() {
        Authentication authentication = new UsernamePasswordAuthenticationToken("Lenny", null, List.of());

        String token = jwtTokenProvider.generateRefreshToken(authentication);
        String username = jwtTokenProvider.getUsername(token);
        assertThat(username).isEqualTo("Lenny");
    }

    @Test
    @DisplayName("getUsername: invalid token throws JwtException")
    void getUsername_sad_invalidTokenThrows() {
        String badToken = "not.a.token";
        assertThatThrownBy(() -> jwtTokenProvider.getUsername(badToken)).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("getAuthentication: valid token returns Authentication with principal and authorities")
    void getAuthentication_happyPath_returnsAuthToken() {
        UserDetailsService userDetailsService = username -> new User(username, "", List.of(new SimpleGrantedAuthority("ROLE_USER")));

        JwtTokenProvider provider = new JwtTokenProvider(null, userDetailsService);
        ReflectionTestUtils.setField(provider, "base64secret", base64Secret);
        ReflectionTestUtils.setField(provider, "accessTokenValidityInMs", accessTokenValidityInMs);
        ReflectionTestUtils.setField(provider, "refreshTokenValidityInMs", refreshTokenValidityInMs);
        provider.init();

        Authentication initial = new UsernamePasswordAuthenticationToken("Bill", null, List.of());
        String token = provider.generateAccessToken(initial);

        Authentication result = provider.getAuthentication(token);

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Bill");
        assertThat(result.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_USER");
    }

    @Test
    @DisplayName("getAuthentication: invalid token throws JwtException")
    void getAuthentication_sad_invalidTokenThrows() {
        UserDetailsService userDetailsService = username -> new User(username, "", List.of());
        JwtTokenProvider provider = new JwtTokenProvider(null, userDetailsService);
        ReflectionTestUtils.setField(provider, "base64secret", base64Secret);
        ReflectionTestUtils.setField(provider, "accessTokenValidityInMs", accessTokenValidityInMs);
        ReflectionTestUtils.setField(provider, "refreshTokenValidityInMs", refreshTokenValidityInMs);
        provider.init();

        String badToken = "not.a.token";

        assertThatThrownBy(() -> jwtTokenProvider.getAuthentication(badToken)).isInstanceOf(JwtException.class);
    }
}
