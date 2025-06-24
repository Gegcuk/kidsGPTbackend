package uk.gegc.kidsgptbackend.service.auth.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import uk.gegc.kidsgptbackend.dto.auth.ForgotPasswordRequest;
import uk.gegc.kidsgptbackend.dto.auth.PasswordResetResponse;
import uk.gegc.kidsgptbackend.dto.auth.ResetPasswordRequest;
import uk.gegc.kidsgptbackend.model.auth.PasswordResetToken;
import uk.gegc.kidsgptbackend.model.user.User;
import uk.gegc.kidsgptbackend.repository.auth.PasswordResetTokenRepository;
import uk.gegc.kidsgptbackend.repository.user.UserRepository;
import uk.gegc.kidsgptbackend.service.auth.PasswordResetService;
import uk.gegc.kidsgptbackend.service.email.EmailService;
import uk.gegc.kidsgptbackend.util.TokenGenerator;

import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetServiceImpl implements PasswordResetService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    @Override
    @Transactional
    public PasswordResetResponse initiatePasswordReset(ForgotPasswordRequest request) {
        // Find user by email
        Optional<User> userOpt = userRepository.findByEmail(request.email());
        
        if (userOpt.isEmpty()) {
            // Don't reveal if email exists or not for security
            log.info("Password reset requested for non-existent email: {}", request.email());
            return new PasswordResetResponse(
                "If an account with this email exists, a password reset link has been sent.",
                LocalDateTime.now().plusHours(1)
            );
        }

        User user = userOpt.get();
        
        // Check if user is active
        if (!user.isActive()) {
            log.warn("Password reset requested for inactive user: {}", request.email());
            return new PasswordResetResponse(
                "If an account with this email exists, a password reset link has been sent.",
                LocalDateTime.now().plusHours(1)
            );
        }

        // Invalidate any existing tokens for this user
        tokenRepository.invalidateAllTokensForUser(user.getId(), LocalDateTime.now());

        // Generate new token
        String resetToken = TokenGenerator.generateSecureToken();
        
        // Create and save token
        PasswordResetToken token = new PasswordResetToken();
        token.setToken(resetToken);
        token.setUserId(user.getId());
        token.setEmail(user.getEmail());
        token.setExpiresAt(LocalDateTime.now().plusHours(1));
        
        tokenRepository.save(token);

        // Send email
        try {
            emailService.sendPasswordResetEmail(user.getEmail(), resetToken, user.getUsername());
        } catch (Exception e) {
            log.error("Failed to send password reset email to: {}", user.getEmail(), e);
            // Delete the token if email fails
            tokenRepository.delete(token);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to send password reset email");
        }

        log.info("Password reset initiated for user: {}", user.getUsername());
        
        return new PasswordResetResponse(
            "If an account with this email exists, a password reset link has been sent.",
            token.getExpiresAt()
        );
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        // Find valid token
        Optional<PasswordResetToken> tokenOpt = tokenRepository.findValidTokenByTokenAndExpiresAtAfter(
            request.token(), LocalDateTime.now()
        );

        if (tokenOpt.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired reset token");
        }

        PasswordResetToken token = tokenOpt.get();

        // Find user
        Optional<User> userOpt = userRepository.findById(token.getUserId());
        if (userOpt.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid reset token");
        }

        User user = userOpt.get();

        // Check if user is still active
        if (!user.isActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Account is not active");
        }

        // Update password
        user.setHashedPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        // Mark token as used
        token.setUsed(true);
        token.setUsedAt(LocalDateTime.now());
        tokenRepository.save(token);

        // Invalidate all other tokens for this user
        tokenRepository.invalidateAllTokensForUser(user.getId(), LocalDateTime.now());

        // Send confirmation email
        try {
            emailService.sendPasswordResetConfirmation(user.getEmail(), user.getUsername());
        } catch (Exception e) {
            log.error("Failed to send password reset confirmation email to: {}", user.getEmail(), e);
            // Don't throw exception as password is already reset
        }

        log.info("Password reset completed for user: {}", user.getUsername());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean validateResetToken(String token) {
        return tokenRepository.findValidTokenByTokenAndExpiresAtAfter(token, LocalDateTime.now()).isPresent();
    }
} 