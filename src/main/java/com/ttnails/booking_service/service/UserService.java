package com.ttnails.booking_service.service;

import com.ttnails.booking_service.dto.UserRequest;
import com.ttnails.booking_service.entity.User;
import com.ttnails.booking_service.enums.Role;
import com.ttnails.booking_service.exception.AppException;
import com.ttnails.booking_service.exception.ErrorCode;
import com.ttnails.booking_service.exception.NotFoundException;
import com.ttnails.booking_service.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class UserService {
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @PreAuthorize("hasRole('ADMIN')")
    public List<User> getAllUsers(){
        return userRepository.findAll();
    }
    @PostAuthorize("hasRole('ADMIN') or returnObject.email == authentication.name")
    public User getUserById(UUID uuid) {
        return userRepository.findById(uuid).orElse(null);
    }
    public User createUser(UserRequest userRequest) {
        if(userRepository.existsByEmail(userRequest.getEmail())) {
            throw new AppException(ErrorCode.EMAIL_IS_ALREADY_USED);
        }
        if(userRepository.existsByPhone(userRequest.getPhone())) {
            throw new AppException(ErrorCode.PHONE_IS_ALREADY_USED);
        }
        User user = newUserFromRequest(userRequest);
        return userRepository.save(user);
    }

    /**
     * Pass elements (infos) from request to user
     * @param userRequest request, that need to be handled
     * @return new user
     */
    private User newUserFromRequest(UserRequest userRequest) {
        User user = new User();
        updateUserFromGivenRequest(user, userRequest,false);
        Set<String> roles = new HashSet<>();
        roles.add(Role.USER.name());
        user.setRoles(roles);
        return user;
    }

    @PreAuthorize("hasRole('ADMIN') or @userService.isCurrentUser(#uuid)")
    public User updateUser(UUID uuid, UserRequest userRequest) {
        User user = userRepository.findById(uuid).orElse(null);
        if(user != null) {
            if(userRequest.getEmail() != null && userRepository.existsByEmailAndIdNot(userRequest.getEmail(), uuid)) {
                throw new AppException(ErrorCode.EMAIL_IS_ALREADY_USED);
            }
            if(userRequest.getPhone() != null && userRepository.existsByPhoneAndIdNot(userRequest.getPhone(), uuid)) {
                throw new AppException(ErrorCode.PHONE_IS_ALREADY_USED);
            }
            updateUserFromGivenRequest(user, userRequest,isCurrentUserAdmin());
            return userRepository.save(user);
        } else {
            throw new NotFoundException("Can't not find user with id:" + uuid);
        }
    }

    /**
     * Update user infos bases on given request
     * @param user user, that have to be updated
     * @param userRequest request, that need to be handled
     */
    private void updateUserFromGivenRequest(User user, UserRequest userRequest, boolean canSetRole) {
        user.setName(userRequest.getName());
        user.setPhone(userRequest.getPhone());
        user.setEmail(userRequest.getEmail());
        if (userRequest.getPassword() != null && !userRequest.getPassword().isEmpty()) {
            user.setPassword(passwordEncoder.encode(userRequest.getPassword()));
        }
        user.setAddress(userRequest.getAddress());
        user.setDob(userRequest.getDob());
        user.setGender(userRequest.getGender());
        if (canSetRole && userRequest.getRoles() != null) {
            user.setRoles(userRequest.getRoles());
        }
    }
    @PreAuthorize("hasRole('ADMIN') or @userService.isCurrentUser(#uuid)  ")
    public void deleteUser(UUID uuid) {
        userRepository.deleteById(uuid);
    }
    /**
        Check User's Authority
    */
    private boolean isCurrentUserAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role -> role.equals("ROLE_ADMIN"));
    }

    public boolean isCurrentUser(UUID uuid) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) return false;

        String currentEmail = authentication.getName(); // JWT subject
        User currentUser = userRepository.findByEmail(currentEmail).orElse(null);
        return currentUser != null && currentUser.getId().equals(uuid);
    }

}
