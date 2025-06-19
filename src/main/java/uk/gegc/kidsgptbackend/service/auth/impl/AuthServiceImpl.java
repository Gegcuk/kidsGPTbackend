package uk.gegc.kidsgptbackend.service.auth.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import uk.gegc.kidsgptbackend.dto.auth.AuthLoginRequest;
import uk.gegc.kidsgptbackend.dto.auth.AuthTokensResponse;
import uk.gegc.kidsgptbackend.dto.user.RegisterUserRequest;
import uk.gegc.kidsgptbackend.dto.user.UserDto;
import uk.gegc.kidsgptbackend.exception.UnauthorizedException;
import uk.gegc.kidsgptbackend.mapper.UserMapper;
import uk.gegc.kidsgptbackend.model.user.Role;
import uk.gegc.kidsgptbackend.model.user.RoleName;
import uk.gegc.kidsgptbackend.model.user.User;
import uk.gegc.kidsgptbackend.repository.auth.RevokedTokenRepository;
import uk.gegc.kidsgptbackend.repository.user.RoleRepository;
import uk.gegc.kidsgptbackend.repository.user.UserRepository;
import uk.gegc.kidsgptbackend.security.JwtTokenProvider;
import uk.gegc.kidsgptbackend.service.auth.AuthService;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final RevokedTokenRepository revokedTokenRepository;

    @Override
    public UserDto register(RegisterUserRequest request) {

        if (userRepository.existsByUsername(request.username())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already in use");
        }

        if (userRepository.existsByEmail(request.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already in use");
        }

        User user = new User();
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setHashedPassword(passwordEncoder.encode(request.password()));
        user.setActive(true);

        Role userRole = roleRepository.findByRole(RoleName.ROLE_PARENT.name())
                .orElseThrow(() -> new IllegalStateException("ROLE_PARENT not found"));
        user.setRoles(Set.of(userRole));

        User saved = userRepository.save(user);
        return userMapper.toDto(saved);
    }

    @Override
    public AuthTokensResponse login(AuthLoginRequest loginRequest) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginRequest.usernameOrEmail(), loginRequest.password())
            );

            String accessToken = jwtTokenProvider.generateAccessToken(authentication);
            String refreshToken = jwtTokenProvider.generateRefreshToken(authentication);
            long accessExpiresInMs = jwtTokenProvider.getAccessTokenValidityInMs();
            long refreshExpiresInMs = jwtTokenProvider.getRefreshTokenValidityInMs();

            return new AuthTokensResponse(accessToken, refreshToken, accessExpiresInMs, refreshExpiresInMs);
        } catch (AuthenticationException ex) {
            throw new UnauthorizedException("Invalid username or password");
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }
    }

    @Override
    public void logout(String token) {
        var claims = jwtTokenProvider.getClaims(token);
        LocalDateTime expires = claims.getExpiration().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        uk.gegc.kidsgptbackend.model.auth.RevokedToken revoked = new uk.gegc.kidsgptbackend.model.auth.RevokedToken();
        revoked.setToken(token);
        revoked.setExpiresAt(expires);
        revokedTokenRepository.save(revoked);
    }

}
