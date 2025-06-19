package uk.gegc.kidsgptbackend.service.auth;

import uk.gegc.kidsgptbackend.dto.auth.AuthLoginRequest;
import uk.gegc.kidsgptbackend.dto.auth.AuthTokensResponse;
import uk.gegc.kidsgptbackend.dto.user.RegisterUserRequest;
import uk.gegc.kidsgptbackend.dto.user.UserDto;
import uk.gegc.kidsgptbackend.dto.user.UserProfileDto;

public interface AuthService {
    UserDto register(RegisterUserRequest request);
    AuthTokensResponse login(AuthLoginRequest request);
    void logout(String token);

    UserProfileDto getProfile(String username);
}
