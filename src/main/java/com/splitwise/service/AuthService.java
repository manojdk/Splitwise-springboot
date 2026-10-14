package com.splitwise.service;

import com.splitwise.domain.User;
import com.splitwise.exception.ConflictException;
import com.splitwise.exception.UnauthorizedException;
import com.splitwise.repository.UserRepository;
import com.splitwise.security.LoginThrottle;
import com.splitwise.security.TokenService;
import com.splitwise.web.dto.LoginRequest;
import com.splitwise.web.dto.RegisterRequest;
import com.splitwise.web.dto.TokenResponse;
import com.splitwise.web.dto.UserResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final LoginThrottle throttle;
    /** Checked against when the email is unknown, so that answer takes as long as a wrong password. */
    private final String unknownUserHash;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, TokenService tokenService,
                       LoginThrottle throttle) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.throttle = throttle;
        this.unknownUserHash = passwordEncoder.encode("no account has this password");
    }

    @Transactional
    public UserResponse register(RegisterRequest request, String clientAddress) {
        throttle.countRequest(clientAddress);
        String email = normalise(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("Email is already registered");
        }
        User user = new User(request.name().trim(), email, passwordEncoder.encode(request.password()));
        return UserResponse.from(userRepository.save(user));
    }

    /**
     * Checks the password once the throttle allows the attempt. An unknown email gets the same answer as a wrong
     * password, after the same amount of work, so the response does not tell which emails have accounts.
     */
    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request, String clientAddress) {
        throttle.countRequest(clientAddress);
        String email = normalise(request.email());
        throttle.checkAccount(email);
        User user = userRepository.findByEmailIgnoreCase(email).orElse(null);
        boolean matches = passwordEncoder.matches(request.password(),
                user != null ? user.getPasswordHash() : unknownUserHash);
        if (user == null || !matches) {
            throttle.recordFailure(email);
            throw new UnauthorizedException("Invalid email or password");
        }
        throttle.recordSuccess(email);
        return tokenService.issue(user);
    }

    private static String normalise(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
