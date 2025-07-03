package uk.gegc.kidsgptbackend.service.auth.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import uk.gegc.kidsgptbackend.dto.auth.AuthLoginRequest;
import uk.gegc.kidsgptbackend.dto.auth.AuthTokensResponse;
import uk.gegc.kidsgptbackend.dto.user.KidDto;
import uk.gegc.kidsgptbackend.dto.user.KidRegistrationRequest;
import uk.gegc.kidsgptbackend.dto.user.RegisterUserRequest;
import uk.gegc.kidsgptbackend.dto.user.UserDto;
import uk.gegc.kidsgptbackend.dto.user.UserProfileDto;
import uk.gegc.kidsgptbackend.exception.UnauthorizedException;
import uk.gegc.kidsgptbackend.exception.ValidationException;
import uk.gegc.kidsgptbackend.mapper.UserMapper;
import uk.gegc.kidsgptbackend.model.family.Kid;
import uk.gegc.kidsgptbackend.model.family.Parent;
import uk.gegc.kidsgptbackend.model.user.Role;
import uk.gegc.kidsgptbackend.model.user.RoleName;
import uk.gegc.kidsgptbackend.model.user.User;
import uk.gegc.kidsgptbackend.repository.auth.RevokedTokenRepository;
import uk.gegc.kidsgptbackend.repository.family.KidRepository;
import uk.gegc.kidsgptbackend.repository.family.ParentRepository;
import uk.gegc.kidsgptbackend.repository.user.RoleRepository;
import uk.gegc.kidsgptbackend.repository.user.UserRepository;
import uk.gegc.kidsgptbackend.security.JwtTokenProvider;
import uk.gegc.kidsgptbackend.service.auth.AuthService;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final ParentRepository parentRepository;
    private final KidRepository kidRepository;
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
        user.setRoles(new HashSet<>(Set.of(userRole)));

        User saved = userRepository.save(user);
        return userMapper.toDto(saved);
    }

    @Override
    @Transactional
    public KidDto registerKid(KidRegistrationRequest request, String parentUsername) {
        // Find parent user
        User parentUser = userRepository.findByUsername(parentUsername)
                .orElseThrow(() -> new ValidationException("Parent user not found"));

        // Verify parent has ROLE_PARENT
        boolean isParent = parentUser.getRoles().stream()
                .anyMatch(role -> RoleName.ROLE_PARENT.name().equals(role.getRole()));
        if (!isParent) {
            throw new ValidationException("Only parents can create kid accounts");
        }

        // Find parent profile
        Parent parent = parentRepository.findByEmail(parentUser.getEmail())
                .orElseThrow(() -> new ValidationException("Parent profile not found"));

        // Generate unique username for kid (nickname + random suffix)
        String kidUsername = generateUniqueKidUsername(request.nickname());

        // Check if username already exists (double-check)
        if (userRepository.existsByUsername(kidUsername)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Generated username already in use");
        }

        // Create User account for kid
        User kidUser = new User();
        kidUser.setUsername(kidUsername);
        kidUser.setEmail(kidUsername + "@kid.local"); // Fake email for kids
        kidUser.setHashedPassword(passwordEncoder.encode(request.password()));
        kidUser.setActive(true);

        Role kidRole = roleRepository.findByRole(RoleName.ROLE_CHILD.name())
                .orElseThrow(() -> new IllegalStateException("ROLE_CHILD not found"));
        kidUser.setRoles(new HashSet<>(Set.of(kidRole)));

        User savedKidUser = userRepository.save(kidUser);

        // Create Kid profile
        Kid kid = new Kid();
        kid.setNickname(request.nickname());
        kid.setAgeGroup(request.ageGroup());
        kid.setParent(parent);
        kid.setUser(savedKidUser);

        Kid savedKid = kidRepository.save(kid);

        return UserMapper.toKidDto(savedKid);
    }

    private String generateUniqueKidUsername(String nickname) {
        String baseUsername = nickname.toLowerCase().replaceAll("[^a-z0-9]", "");
        String kidUsername = baseUsername + "_kid";
        
        int counter = 1;
        while (userRepository.existsByUsername(kidUsername)) {
            kidUsername = baseUsername + "_kid" + counter;
            counter++;
        }
        
        return kidUsername;
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

    @Override
    @Transactional(readOnly = true)
    public UserProfileDto getProfile(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return userMapper.toProfileDto(user);
    }
}
