package uk.gegc.kidsgptbackend.service.family.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.transaction.annotation.Transactional;
import uk.gegc.kidsgptbackend.dto.user.ChildProfileDto;
import uk.gegc.kidsgptbackend.dto.user.ChildProfileUpdateRequest;
import uk.gegc.kidsgptbackend.exception.ValidationException;
import uk.gegc.kidsgptbackend.mapper.UserMapper;
import uk.gegc.kidsgptbackend.model.family.Kid;
import uk.gegc.kidsgptbackend.model.family.Parent;
import uk.gegc.kidsgptbackend.model.user.Role;
import uk.gegc.kidsgptbackend.model.user.RoleName;
import uk.gegc.kidsgptbackend.model.user.User;
import uk.gegc.kidsgptbackend.repository.family.KidRepository;
import uk.gegc.kidsgptbackend.repository.family.ParentRepository;
import uk.gegc.kidsgptbackend.repository.user.UserRepository;
import uk.gegc.kidsgptbackend.service.family.KidProfileService;

import java.util.Optional;

@Service
public class KidProfileServiceImpl implements KidProfileService {
    @Autowired
    private KidRepository kidRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ParentRepository parentRepository;

    @Override
    @Transactional
    public ChildProfileDto updateCurrentChildProfile(ChildProfileUpdateRequest request) {
        // Get authenticated username
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        
        Kid kid;
        
        // Check if user is a parent or a child
        boolean isParent = user.getRoles().stream()
                .anyMatch(role -> RoleName.ROLE_PARENT.name().equals(role.getRole()));
        boolean isChild = user.getRoles().stream()
                .anyMatch(role -> RoleName.ROLE_CHILD.name().equals(role.getRole()));
        
        if (isParent) {
            // Parent updating kid profile - find parent by email, then kid by parent
            Parent parent = parentRepository.findByEmail(user.getEmail())
                    .orElseThrow(() -> new ValidationException("Parent profile not found for user"));
            
            kid = kidRepository.findByParentId(parent.getId())
                    .orElseThrow(() -> new ValidationException("Child profile not found for user"));
        } else if (isChild) {
            // Kid updating their own profile - find kid directly by user
            kid = kidRepository.findByUserId(user.getId())
                    .orElseThrow(() -> new ValidationException("Child profile not found for user"));
        } else {
            throw new ValidationException("User must be either a parent or child to update child profile");
        }
        
        // Update Kid from request
        UserMapper.updateKidFromRequest(kid, request);
        kidRepository.save(kid);
        return UserMapper.toChildProfileDto(kid);
    }
} 