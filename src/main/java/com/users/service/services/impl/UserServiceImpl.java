package com.users.service.services.impl;

import com.users.service.configs.JwtTokenProvider;
import com.users.service.documents.User;
import com.users.service.dtos.*;
import com.users.service.exceptions.DuplicateUserException;
import com.users.service.exceptions.UserNotFoundException;
import com.users.service.repositories.UserRepository;
import com.users.service.services.UserService;
import com.users.service.services.objectstore.ObjectStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final ObjectStorageService objectStorageService;
    private final Random random = new Random();


    // Generate custom user ID
    private String generateUserId() {
        // Generate a number and convert to string with base 36 (alphanumeric)
        String randomPart = Integer.toString(random.nextInt(1679616), 36); // 36^4 = 1679616
        // Pad with leading zeros if necessary and take last 4 characters
        while (randomPart.length() < 4) {
            randomPart = "0" + randomPart;
        }
        return randomPart;
    }

    // Register a new user
    @Override
    public UserRegistrationResp registerUser(UserRegistrationDTO request) {
        // Check if username already exists
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new DuplicateUserException("Username already exists");
        }

        // Check if email already exists
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateUserException("Email already exists");
        }

        // Create new user
        User user = new User();
        String userId = generateUserId();
        user.setId(userId);
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setEmail(request.getEmail());
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        user.setActive(true);

        // Save user
        userRepository.save(user);

        // Generate JWT token
        String token = jwtTokenProvider.generateToken(user.getUsername(), List.of("ROLE_USER"));
        return new UserRegistrationResp(userId, token);
    }

    // Login user
    @Override
    public UserLoginResp loginUser(UserLoginReq request) {
        // Find user by username
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new UserNotFoundException("Invalid username or password"));

        // Verify password
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new UserNotFoundException("Invalid username or password");
        }

        // Generate JWT token
        String token = jwtTokenProvider.generateToken(user.getUsername(), List.of("ROLE_USER"));
        return new UserLoginResp(user.getId(), token);
    }

    // Get user by ID
    @Override
    public UserResp getUserById(String id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + id));
        return new UserResp(user);
    }

    // Get all users by IDs with pagination
    @Override
    public Page<UserResp> getAllUsersByIds(List<String> ids, Pageable pageable) {
        Page<User> users = userRepository.findAllByIdIn(ids, pageable);
        return users.map(UserResp::new);
    }

    // Get all users with pagination
    @Override
    public Page<UserResp> getAllUsers(Pageable pageable) {
        Page<User> users = userRepository.findAll(pageable);
        return users.map(UserResp::new);
    }

    @Override
    public UserResp uploadProfilePicture(String userId, MultipartFile file) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + userId));

        // Delete old profile picture if exists
        if (user.getProfileImageUrl() != null) {
            objectStorageService.deleteProfilePicture(user.getProfileImageUrl());
        }

        // Upload new profile picture
        String profilePictureUrl = objectStorageService.uploadProfilePicture(userId, file);

        // Update user
        user.setProfileImageUrl(profilePictureUrl);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        return convertToUserResponse(user);
    }

    private UserResp convertToUserResponse(User user) {
        UserResp response = new UserResp();
        response.setId(user.getId());
        response.setUsername(user.getUsername());
        response.setEmail(user.getEmail());
        response.setFirstName(user.getFirstName());
        response.setLastName(user.getLastName());
        response.setProfileImageUrl(user.getProfileImageUrl());
        return response;
    }
}
