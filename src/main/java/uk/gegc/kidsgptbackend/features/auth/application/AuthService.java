package uk.gegc.kidsgptbackend.features.auth.application;

import uk.gegc.kidsgptbackend.features.auth.api.dto.AuthLoginRequest;
import uk.gegc.kidsgptbackend.features.auth.api.dto.AuthTokensResponse;
import uk.gegc.kidsgptbackend.features.auth.api.dto.UpdateEmailRequest;
import uk.gegc.kidsgptbackend.features.auth.api.dto.UpdatePasswordRequest;
import uk.gegc.kidsgptbackend.features.user.api.dto.*;

import java.util.List;
import java.util.UUID;

public interface AuthService {
    UserDto register(RegisterUserRequest request);

    KidDto registerKid(KidRegistrationRequest request, String parentUsername);

    AuthTokensResponse login(AuthLoginRequest request);

    void logout(String token);

    UserProfileDto getProfile(String username);

    List<KidDto> getParentKids(String parentUsername);
    
    void deleteKid(UUID kidId, String parentUsername);
    
    void deleteParentAccount(String parentUsername);
    
    UserProfileDto updateEmail(String username, UpdateEmailRequest request);
    
    UserProfileDto updatePassword(String username, UpdatePasswordRequest request);
}
