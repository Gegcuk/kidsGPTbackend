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
import uk.gegc.kidsgptbackend.model.user.User;
import uk.gegc.kidsgptbackend.repository.family.KidRepository;
import uk.gegc.kidsgptbackend.repository.user.UserRepository;
import uk.gegc.kidsgptbackend.service.family.KidProfileService;

import java.util.Optional;
import java.util.UUID;

@Service
public class KidProfileServiceImpl implements KidProfileService {
    @Autowired
    private KidRepository kidRepository;
    @Autowired
    private UserRepository userRepository;

    @Override
    @Transactional
    public ChildProfileDto updateCurrentChildProfile(ChildProfileUpdateRequest request) {
        // Get authenticated username
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        // Find Kid by user (assume 1:1 mapping for now, or adjust as needed)
        Optional<Kid> kidOpt = kidRepository.findByParentId(user.getId());
        Kid kid = kidOpt.orElseThrow(() -> new ValidationException("Child profile not found for user"));
        // Update Kid from request
        UserMapper.updateKidFromRequest(kid, request);
        kidRepository.save(kid);
        return UserMapper.toChildProfileDto(kid);
    }
} 