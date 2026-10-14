package com.splitwise.service;

import com.splitwise.domain.User;
import com.splitwise.exception.BadRequestException;
import com.splitwise.exception.ForbiddenException;
import com.splitwise.security.LoginThrottle;
import com.splitwise.security.TokenService;
import com.splitwise.web.dto.ChangePasswordRequest;
import com.splitwise.web.dto.TokenResponse;
import com.splitwise.web.dto.UpdateProfileRequest;
import com.splitwise.web.dto.UserResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The signed-in user's own account: name, password, and ending their sessions. */
@Service
public class AccountService {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final LoginThrottle throttle;

    public AccountService(UserService userService, PasswordEncoder passwordEncoder, TokenService tokenService,
                          LoginThrottle throttle) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.throttle = throttle;
    }

    @Transactional
    public UserResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = userService.getById(userId);
        user.rename(request.name().trim());
        return UserResponse.from(user);
    }

    /**
     * Needs the current password, under the same throttle as sign-in (a stolen token must not become a way to guess
     * the password). Every other session ends; the caller gets a fresh token, so this device stays signed in.
     */
    @Transactional
    public TokenResponse changePassword(Long userId, ChangePasswordRequest request, String clientAddress) {
        throttle.countRequest(clientAddress);
        User user = userService.getById(userId);
        throttle.checkAccount(user.getEmail());
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throttle.recordFailure(user.getEmail());
            throw new ForbiddenException("Current password is incorrect");
        }
        throttle.recordSuccess(user.getEmail());
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new BadRequestException("The new password is the same as the current one");
        }
        user.changePassword(passwordEncoder.encode(request.newPassword()));
        return tokenService.issue(user);
    }

    /** Signs out every device, this one included. */
    @Transactional
    public void endAllSessions(Long userId) {
        userService.getById(userId).endAllSessions();
    }
}
