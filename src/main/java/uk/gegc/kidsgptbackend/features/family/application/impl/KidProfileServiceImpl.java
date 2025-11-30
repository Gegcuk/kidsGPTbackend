package uk.gegc.kidsgptbackend.features.family.application.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gegc.kidsgptbackend.features.user.api.dto.ChildProfileDto;
import uk.gegc.kidsgptbackend.features.user.api.dto.ChildProfileUpdateRequest;
import uk.gegc.kidsgptbackend.features.user.api.dto.KidSelfUpdateRequest;
import uk.gegc.kidsgptbackend.features.user.api.dto.ParentUpdateKidRequest;
import uk.gegc.kidsgptbackend.shared.exception.ValidationException;
import uk.gegc.kidsgptbackend.features.user.infra.mapping.UserMapper;
import uk.gegc.kidsgptbackend.features.family.domain.model.Kid;
import uk.gegc.kidsgptbackend.features.family.domain.model.Parent;
import uk.gegc.kidsgptbackend.features.user.domain.model.RoleName;
import uk.gegc.kidsgptbackend.features.user.domain.model.User;
import uk.gegc.kidsgptbackend.features.family.domain.repository.KidRepository;
import uk.gegc.kidsgptbackend.features.family.domain.repository.ParentRepository;
import uk.gegc.kidsgptbackend.features.user.domain.repository.UserRepository;
import uk.gegc.kidsgptbackend.features.family.application.KidProfileService;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class KidProfileServiceImpl implements KidProfileService {
    private final KidRepository kidRepository;
    private final UserRepository userRepository;
    private final ParentRepository parentRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public ChildProfileDto updateKidSelfProfile(KidSelfUpdateRequest request) {
        // Get authenticated username
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        
        // Verify user is a child
        boolean isChild = user.getRoles() != null && user.getRoles().stream()
                .anyMatch(role -> RoleName.ROLE_CHILD.name().equals(role.getRole()));
        
        if (!isChild) {
            throw new ValidationException("Only children can update their own profiles");
        }
        
        // Find kid by user ID
        Kid kid = kidRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ValidationException("Child profile not found"));
        
        // Update avatar only (no avatar logic implemented yet)
        if (request.avatarId() != null) {
            kid.setAvatarId(request.avatarId());
        }
        
        kidRepository.save(kid);
        return UserMapper.toChildProfileDto(kid);
    }

    @Override
    @Transactional
    public ChildProfileDto updateKidProfileByParent(UUID kidId, ParentUpdateKidRequest request) {
        // Get authenticated username
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        
        // Verify user is a parent
        boolean isParent = user.getRoles() != null && user.getRoles().stream()
                .anyMatch(role -> RoleName.ROLE_PARENT.name().equals(role.getRole()));
        
        if (!isParent) {
            throw new ValidationException("Only parents can update their kids' profiles");
        }
        
        // Find parent profile - prefer userId lookup, fallback to email
        Parent parent = parentRepository.findByUserId(user.getId())
                .or(() -> parentRepository.findByEmail(user.getEmail()))
                .orElseThrow(() -> new ValidationException("Parent profile not found"));
        
        // Find kid by ID and verify it belongs to this parent
        Kid kid = kidRepository.findById(kidId)
                .orElseThrow(() -> new ValidationException("Kid not found"));
        
        if (!kid.getParent().getId().equals(parent.getId())) {
            throw new ValidationException("You can only update your own kids' profiles");
        }
        
        // Update nickname
        if (request.nickname() != null) {
            kid.setNickname(request.nickname());
        }
        
        // Update age group
        if (request.ageGroup() != null) {
            kid.setAgeGroup(request.ageGroup());
        }
        
        // Update password if provided
        if (request.password() != null && !request.password().trim().isEmpty()) {
            User kidUser = kid.getUser();
            kidUser.setHashedPassword(passwordEncoder.encode(request.password()));
            userRepository.save(kidUser);
        }
        
        kidRepository.save(kid);
        return UserMapper.toChildProfileDto(kid);
    }

    @Override
    @Transactional
    @Deprecated
    public ChildProfileDto updateCurrentChildProfile(ChildProfileUpdateRequest request) {
        // Legacy method - redirects to kid self-update for backward compatibility
        // This assumes only kids are using this old endpoint
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        
        boolean isChild = user.getRoles() != null && user.getRoles().stream()
                .anyMatch(role -> RoleName.ROLE_CHILD.name().equals(role.getRole()));
        
        if (!isChild) {
            throw new ValidationException("Only children can update their own profiles");
        }
        
        Kid kid = kidRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ValidationException("Child profile not found"));
        
        // Update Kid from legacy request (same as before)
        UserMapper.updateKidFromRequest(kid, request);
        kidRepository.save(kid);
        return UserMapper.toChildProfileDto(kid);
    }
} 