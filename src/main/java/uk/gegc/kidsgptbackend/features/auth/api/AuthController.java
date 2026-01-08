package uk.gegc.kidsgptbackend.features.auth.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import uk.gegc.kidsgptbackend.features.auth.api.dto.*;
import uk.gegc.kidsgptbackend.features.user.api.dto.*;
import uk.gegc.kidsgptbackend.features.auth.application.AuthService;
import uk.gegc.kidsgptbackend.features.auth.application.PasswordResetService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Authentication, parent account management, and kid provisioning")
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;

    @Operation(summary = "Register a new parent account")
    @PostMapping("/register")
    public ResponseEntity<UserDto> register(
            @Valid @RequestBody RegisterUserRequest request
    ) {
        UserDto createdUser = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdUser);
    }

    @Operation(summary = "Register a kid profile under the current parent", security = @SecurityRequirement(name = "bearerAuth"))
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

    @Operation(summary = "Authenticate a user and issue access/refresh tokens")
    @PostMapping("/login")
    public ResponseEntity<AuthTokensResponse> login(
            @Valid @RequestBody AuthLoginRequest request
    ) {
        AuthTokensResponse tokens = authService.login(request);
        return ResponseEntity.ok(tokens);
    }

    @Operation(summary = "Logout by revoking the current access token", security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            authService.logout(token);
        }
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Get the current user's profile", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/me")
    public ResponseEntity<UserProfileDto> me(@AuthenticationPrincipal org.springframework.security.core.userdetails.User principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        UserProfileDto profile = authService.getProfile(principal.getUsername());
        return ResponseEntity.ok(profile);
    }

    @Operation(summary = "List kids for the current parent", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/kids")
    @PreAuthorize("hasRole('PARENT')")
    public ResponseEntity<List<KidDto>> getMyKids(@AuthenticationPrincipal org.springframework.security.core.userdetails.User principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        List<KidDto> kids = authService.getParentKids(principal.getUsername());
        return ResponseEntity.ok(kids);
    }

    @Operation(summary = "Initiate password reset (sends email)")
    @PostMapping("/forgot-password")
    public ResponseEntity<PasswordResetResponse> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request
    ) {
        PasswordResetResponse response = passwordResetService.initiatePasswordReset(request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Complete password reset with token")
    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request
    ) {
        passwordResetService.resetPassword(request);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Validate a password reset token")
    @GetMapping("/validate-reset-token")
    public ResponseEntity<Boolean> validateResetToken(@RequestParam String token) {
        boolean isValid = passwordResetService.validateResetToken(token);
        return ResponseEntity.ok(isValid);
    }

    @Operation(summary = "Delete a kid profile", security = @SecurityRequirement(name = "bearerAuth"))
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

    @Operation(summary = "Delete the current parent account", security = @SecurityRequirement(name = "bearerAuth"))
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

    @Operation(summary = "Update parent email", security = @SecurityRequirement(name = "bearerAuth"))
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

    @Operation(summary = "Update parent password", security = @SecurityRequirement(name = "bearerAuth"))
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
