package uk.gegc.kidsgptbackend.service.auth.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import uk.gegc.kidsgptbackend.dto.auth.UpdateEmailRequest;
import uk.gegc.kidsgptbackend.dto.auth.UpdatePasswordRequest;
import uk.gegc.kidsgptbackend.features.user.api.dto.*;
import uk.gegc.kidsgptbackend.shared.exception.CredentialUpdateException;
import uk.gegc.kidsgptbackend.shared.exception.UnauthorizedException;
import uk.gegc.kidsgptbackend.shared.exception.ValidationException;
import uk.gegc.kidsgptbackend.features.user.infra.mapping.UserMapper;
import uk.gegc.kidsgptbackend.model.family.Kid;
import uk.gegc.kidsgptbackend.model.family.Parent;
import uk.gegc.kidsgptbackend.features.user.domain.model.Role;
import uk.gegc.kidsgptbackend.features.user.domain.model.RoleName;
import uk.gegc.kidsgptbackend.features.user.domain.model.User;
import uk.gegc.kidsgptbackend.repository.auth.RevokedTokenRepository;
import uk.gegc.kidsgptbackend.repository.family.KidRepository;
import uk.gegc.kidsgptbackend.repository.family.ParentRepository;
import uk.gegc.kidsgptbackend.features.user.domain.repository.RoleRepository;
import uk.gegc.kidsgptbackend.features.user.domain.repository.UserRepository;
import uk.gegc.kidsgptbackend.shared.security.JwtTokenProvider;
import uk.gegc.kidsgptbackend.service.auth.AuthService;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
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
    @Transactional
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
        user.setRoles(new HashSet<>(java.util.Arrays.asList(userRole)));

        User saved = userRepository.save(user);

        // Create parent profile for parent users
        if (userRole.getRole().equals(RoleName.ROLE_PARENT.name())) {
            Parent parent = new Parent();
            parent.setFirstName("Parent"); // Default first name
            parent.setLastName("User");    // Default last name
            parent.setEmail(request.email());
            parent.setUserId(saved.getId()); // Set direct userId reference for robust lookup
            parentRepository.save(parent);
        }

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

        // Find parent profile - prefer userId lookup, fallback to email
        Optional<Parent> parentOpt = parentRepository.findByUserId(parentUser.getId());
        if (parentOpt.isEmpty()) {
            log.debug("Parent profile not found by userId for user: {}, trying email lookup", parentUser.getUsername());
            parentOpt = parentRepository.findByEmail(parentUser.getEmail());
        }
        Parent parent = parentOpt.orElseThrow(() -> new ValidationException("Parent profile not found"));

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
        kidUser.setRoles(new HashSet<>(java.util.Arrays.asList(kidRole)));

        User savedKidUser = userRepository.save(kidUser);

        // Create Kid profile
        Kid kid = new Kid();
        kid.setNickname(request.nickname());
        kid.setAgeGroup(request.ageGroup());
        kid.setParent(parent);
        kid.setUser(savedKidUser);

        // Add kid to parent's collection to maintain bidirectional relationship
        parent.getKids().add(kid);

        Kid savedKid = kidRepository.save(kid);

        return UserMapper.toKidDto(savedKid);
    }

    private String generateUniqueKidUsername(String nickname) {
        String baseUsername = nickname.toLowerCase().replaceAll("[^a-z0-9]", "");
        if (baseUsername.isBlank()) {
            baseUsername = "kid"; // Fallback if nickname becomes empty after sanitization
        }
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

    @Override
    @Transactional(readOnly = true)
    public List<KidDto> getParentKids(String parentUsername) {
        // Find parent user
        User parentUser = userRepository.findByUsername(parentUsername)
                .orElseThrow(() -> new ValidationException("Parent user not found"));

        // Verify parent has ROLE_PARENT
        boolean isParent = parentUser.getRoles().stream()
                .anyMatch(role -> RoleName.ROLE_PARENT.name().equals(role.getRole()));
        if (!isParent) {
            throw new ValidationException("Only parents can retrieve their kids");
        }

        // Find parent profile - prefer userId lookup, fallback to email
        Optional<Parent> parentOpt = parentRepository.findByUserId(parentUser.getId());
        if (parentOpt.isEmpty()) {
            log.debug("Parent profile not found by userId for user: {}, trying email lookup", parentUser.getUsername());
            parentOpt = parentRepository.findByEmail(parentUser.getEmail());
        }
        Parent parent = parentOpt.orElseThrow(() -> new ValidationException("Parent profile not found"));

        // Find all kids belonging to this parent
        List<Kid> kids = kidRepository.findAllByParentId(parent.getId());

        // Convert to DTOs
        return kids.stream()
                .map(UserMapper::toKidDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteKid(UUID kidId, String parentUsername) {
        // Find parent user
        User parentUser = userRepository.findByUsername(parentUsername)
                .orElseThrow(() -> new ValidationException("Parent user not found"));

        // Verify parent has ROLE_PARENT
        boolean isParent = parentUser.getRoles().stream()
                .anyMatch(role -> RoleName.ROLE_PARENT.name().equals(role.getRole()));
        if (!isParent) {
            throw new ValidationException("Only parents can delete kid accounts");
        }

        // Find parent profile - prefer userId lookup, fallback to email
        Optional<Parent> parentOpt = parentRepository.findByUserId(parentUser.getId());
        if (parentOpt.isEmpty()) {
            log.debug("Parent profile not found by userId for user: {}, trying email lookup", parentUser.getUsername());
            parentOpt = parentRepository.findByEmail(parentUser.getEmail());
        }
        Parent parent = parentOpt.orElseThrow(() -> new ValidationException("Parent profile not found"));

        // Find the kid by ID and verify it belongs to this parent
        Kid kid = kidRepository.findById(kidId)
                .orElseThrow(() -> new ValidationException("Kid not found"));

        if (!kid.getParent().getId().equals(parent.getId())) {
            throw new ValidationException("You can only delete your own kids' accounts");
        }

        // Check if kid has a user account
        if (kid.getUser() == null) {
            throw new ValidationException("Kid user account not found");
        }

        // Remove kid from parent's collection to maintain bidirectional relationship
        parent.getKids().remove(kid);

        // Delete the kid's user account
        userRepository.delete(kid.getUser());

        // Delete the kid profile
        kidRepository.delete(kid);
    }

    @Override
    @Transactional
    public void deleteParentAccount(String parentUsername) {
        // Find parent user
        User parentUser = userRepository.findByUsername(parentUsername)
                .orElseThrow(() -> new ValidationException("Parent user not found"));

        // Verify parent has ROLE_PARENT
        boolean isParent = parentUser.getRoles().stream()
                .anyMatch(role -> RoleName.ROLE_PARENT.name().equals(role.getRole()));
        if (!isParent) {
            throw new ValidationException("Only parents can delete their accounts");
        }

        // Find parent profile - prefer userId lookup, fallback to email
        Optional<Parent> parentOpt = parentRepository.findByUserId(parentUser.getId());
        if (parentOpt.isEmpty()) {
            log.debug("Parent profile not found by userId for user: {}, trying email lookup", parentUser.getUsername());
            parentOpt = parentRepository.findByEmail(parentUser.getEmail());
        }
        Parent parent = parentOpt.orElseThrow(() -> new ValidationException("Parent profile not found"));

        // Check if parent has kids by querying the database directly
        List<Kid> existingKids = kidRepository.findAllByParentId(parent.getId());
        if (!existingKids.isEmpty()) {
            throw new ValidationException("Cannot delete parent account with existing kids. Please delete all kids first.");
        }

        // Delete the parent's user account
        userRepository.delete(parentUser);

        // Delete the parent profile
        parentRepository.delete(parent);
    }

    @Override
    @Transactional
    public UserProfileDto updateEmail(String username, UpdateEmailRequest request) {
        // Find the user
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        // Check if the new email is already in use by another user
        if (userRepository.existsByEmail(request.newEmail()) && 
            !user.getEmail().equals(request.newEmail())) {
            throw new CredentialUpdateException("Email already in use");
        }

        // Update the email
        user.setEmail(request.newEmail());
        User savedUser = userRepository.save(user);

        return userMapper.toProfileDto(savedUser);
    }

    @Override
    @Transactional
    public UserProfileDto updatePassword(String username, UpdatePasswordRequest request) {
        // Find the user
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        // Verify current password
        if (!passwordEncoder.matches(request.currentPassword(), user.getHashedPassword())) {
            throw new CredentialUpdateException("Current password is incorrect");
        }

        // Check if new password is the same as current password
        if (passwordEncoder.matches(request.newPassword(), user.getHashedPassword())) {
            throw new CredentialUpdateException("New password must be different from current password");
        }

        // Update the password
        user.setHashedPassword(passwordEncoder.encode(request.newPassword()));
        User savedUser = userRepository.save(user);

        return userMapper.toProfileDto(savedUser);
    }
}
