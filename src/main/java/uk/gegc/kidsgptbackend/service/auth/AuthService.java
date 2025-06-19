package uk.gegc.kidsgptbackend.service.auth;

import uk.gegc.kidsgptbackend.dto.user.RegisterUserRequest;
import uk.gegc.kidsgptbackend.dto.user.UserDto;

public interface AuthService {
    UserDto register(RegisterUserRequest request);

}
