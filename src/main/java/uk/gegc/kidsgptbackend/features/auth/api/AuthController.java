package uk.gegc.kidsgptbackend.features.auth.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import uk.gegc.kidsgptbackend.features.auth.api.dto.*;
import uk.gegc.kidsgptbackend.features.user.api.dto.*;
import uk.gegc.kidsgptbackend.features.auth.application.AuthService;
import uk.gegc.kidsgptbackend.features.auth.application.PasswordResetService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;

    @PostMapping("/register")
    public ResponseEntity<UserDto> register(
            @Valid @RequestBody RegisterUserRequest request
    ) {
        UserDto createdUser = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdUser);
    }



    @PostMapping("/register-kid")
    public ResponseEntity<KidDto> registerKid(
            @Valid @RequestBody KidRegistrationRequest request,
            @AuthenticationPrincipal org.springframework.security.core.userdetails.User principal
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        KidDto createdKid = authService.registerKid(request, principal.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(createdKid);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthTokensResponse> login(
            @Valid @RequestBody AuthLoginRequest request
    ) {
        AuthTokensResponse tokens = authService.login(request);
        return ResponseEntity.ok(tokens);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            authService.logout(token);
        }
        return ResponseEntity.ok().build();
    }

    @GetMapping("/me")
    public ResponseEntity<UserProfileDto> me(@AuthenticationPrincipal org.springframework.security.core.userdetails.User principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        UserProfileDto profile = authService.getProfile(principal.getUsername());
        return ResponseEntity.ok(profile);
    }

    @GetMapping("/kids")
    @PreAuthorize("hasRole('PARENT')")
    public ResponseEntity<List<KidDto>> getMyKids(@AuthenticationPrincipal org.springframework.security.core.userdetails.User principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        List<KidDto> kids = authService.getParentKids(principal.getUsername());
        return ResponseEntity.ok(kids);
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<PasswordResetResponse> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request
    ) {
        PasswordResetResponse response = passwordResetService.initiatePasswordReset(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request
    ) {
        passwordResetService.resetPassword(request);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/validate-reset-token")
    public ResponseEntity<Boolean> validateResetToken(@RequestParam String token) {
        boolean isValid = passwordResetService.validateResetToken(token);
        return ResponseEntity.ok(isValid);
    }

    @DeleteMapping("/kids/{kidId}")
    @PreAuthorize("hasRole('PARENT')")
    public ResponseEntity<Void> deleteKid(
            @PathVariable UUID kidId,
            @AuthenticationPrincipal org.springframework.security.core.userdetails.User principal
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        authService.deleteKid(kidId, principal.getUsername());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/account")
    @PreAuthorize("hasRole('PARENT')")
    public ResponseEntity<Void> deleteParentAccount(
            @AuthenticationPrincipal org.springframework.security.core.userdetails.User principal
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        authService.deleteParentAccount(principal.getUsername());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/update-email")
    public ResponseEntity<UserProfileDto> updateEmail(
            @Valid @RequestBody UpdateEmailRequest request,
            @AuthenticationPrincipal org.springframework.security.core.userdetails.User principal
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        UserProfileDto updatedProfile = authService.updateEmail(principal.getUsername(), request);
        return ResponseEntity.ok(updatedProfile);
    }

    @PutMapping("/update-password")
    public ResponseEntity<UserProfileDto> updatePassword(
            @Valid @RequestBody UpdatePasswordRequest request,
            @AuthenticationPrincipal org.springframework.security.core.userdetails.User principal
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        UserProfileDto updatedProfile = authService.updatePassword(principal.getUsername(), request);
        return ResponseEntity.ok(updatedProfile);
    }
}
