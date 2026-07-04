package com.app.ehps.auth;

import com.app.ehps.auth.dto.AuthResponse;
import com.app.ehps.auth.dto.LoginRequest;
import com.app.ehps.auth.dto.RegisterRequest;
import com.app.ehps.auth.dto.UserResponse;
import com.app.ehps.security.JwtService;
import com.app.ehps.user.User;
import com.app.ehps.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Registration and login business logic (BEHAVIOR-BASELINE.md §2, §3).
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository,
                        PasswordEncoder passwordEncoder,
                        AuthenticationManager authenticationManager,
                        JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    public UserResponse register(RegisterRequest request) {
        String email = request.getEmail().trim();

        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "User already exists with email: " + email
            );
        }

        User user = new User();
        user.setName(request.getName().trim());
        user.setEmail(email);
        user.setPhone(request.getPhone() == null ? null : request.getPhone().trim());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(request.getRole());
        user.setSpeciality(request.getSpeciality() == null ? null : request.getSpeciality().trim());

        User savedUser = userRepository.save(user);

        return mapToUserResponse(savedUser);
    }

    public AuthResponse login(LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getEmail().trim(),
                            request.getPassword()
                    )
            );
        } catch (AuthenticationException ex) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid email or password"
            );
        }

        User user = userRepository.findByEmail(request.getEmail().trim())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Invalid email or password"
                ));

        String token = jwtService.generateToken(user);

        return new AuthResponse(token, mapToUserResponse(user));
    }

    private UserResponse mapToUserResponse(User user) {
        return new UserResponse(
                user.getEmpId(),
                user.getName(),
                user.getEmail(),
                user.getPhone(),
                user.getRole().getDbValue(),
                user.getSpeciality()
        );
    }
}
