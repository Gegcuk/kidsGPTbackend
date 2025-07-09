package uk.gegc.kidsgptbackend.service.auth;

import uk.gegc.kidsgptbackend.dto.auth.AuthLoginRequest;
import uk.gegc.kidsgptbackend.dto.auth.AuthTokensResponse;
import uk.gegc.kidsgptbackend.dto.auth.UpdateEmailRequest;
import uk.gegc.kidsgptbackend.dto.auth.UpdatePasswordRequest;
import uk.gegc.kidsgptbackend.dto.user.KidDto;
import uk.gegc.kidsgptbackend.dto.user.KidRegistrationRequest;
import uk.gegc.kidsgptbackend.dto.user.ParentDto;
import uk.gegc.kidsgptbackend.dto.user.RegisterUserRequest;
import uk.gegc.kidsgptbackend.dto.user.UserDto;
import uk.gegc.kidsgptbackend.dto.user.UserProfileDto;

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
