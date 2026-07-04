package com.app.ehps.auth;

import com.app.ehps.auth.dto.AuthResponse;
import com.app.ehps.auth.dto.LoginRequest;
import com.app.ehps.auth.dto.RegisterRequest;
import com.app.ehps.auth.dto.UserResponse;
import com.app.ehps.common.constant.Role;
import com.app.ehps.security.JwtService;
import com.app.ehps.user.User;
import com.app.ehps.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtService jwtService;

    private AuthService authService() {
        return new AuthService(userRepository, passwordEncoder, authenticationManager, jwtService);
    }

    @Test
    void register_success_savesEncodedPasswordAndReturnsUserResponse() {
        RegisterRequest request = new RegisterRequest();
        request.setName("  Jane Doe  ");
        request.setEmail("jane@ehps.com");
        request.setPhone("9876543210");
        request.setPassword("Password@123");
        request.setRole(Role.TECHNICIAN);
        request.setSpeciality("  lithography  ");

        when(userRepository.existsByEmailIgnoreCase("jane@ehps.com")).thenReturn(false);
        when(passwordEncoder.encode("Password@123")).thenReturn("ENCODED_HASH");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setEmpId(10001L);
            return u;
        });

        UserResponse response = authService().register(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();

        assertThat(saved.getName()).isEqualTo("Jane Doe");
        assertThat(saved.getEmail()).isEqualTo("jane@ehps.com");
        assertThat(saved.getPhone()).isEqualTo("9876543210");
        assertThat(saved.getPassword()).isEqualTo("ENCODED_HASH");
        assertThat(saved.getRole()).isEqualTo(Role.TECHNICIAN);
        assertThat(saved.getSpeciality()).isEqualTo("lithography");

        assertThat(response.getEmpId()).isEqualTo(10001L);
        assertThat(response.getName()).isEqualTo("Jane Doe");
        assertThat(response.getEmail()).isEqualTo("jane@ehps.com");
        assertThat(response.getPhone()).isEqualTo("9876543210");
        assertThat(response.getRole()).isEqualTo("technician");
        assertThat(response.getSpeciality()).isEqualTo("lithography");
    }

    @Test
    void register_duplicateEmail_throws409() {
        RegisterRequest request = new RegisterRequest();
        request.setName("Jane");
        request.setEmail("jane@ehps.com");
        request.setPhone("9876543210");
        request.setPassword("Password@123");
        request.setRole(Role.TECHNICIAN);

        when(userRepository.existsByEmailIgnoreCase("jane@ehps.com")).thenReturn(true);

        assertThatThrownBy(() -> authService().register(request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode().value()).isEqualTo(409);
                    assertThat(rse.getReason()).isEqualTo("User already exists with email: jane@ehps.com");
                });
    }

    @Test
    void login_success_returnsTokenAndUser() {
        LoginRequest request = new LoginRequest();
        request.setEmail("jane@ehps.com");
        request.setPassword("Password@123");

        User user = new User();
        user.setEmpId(10001L);
        user.setName("Jane Doe");
        user.setEmail("jane@ehps.com");
        user.setPhone("9876543210");
        user.setPassword("ENCODED_HASH");
        user.setRole(Role.TECHNICIAN);
        user.setSpeciality("lithography");

        when(userRepository.findByEmail("jane@ehps.com")).thenReturn(Optional.of(user));
        when(jwtService.generateToken(user)).thenReturn("jwt-token");

        AuthResponse response = authService().login(request);

        assertThat(response.getToken()).isEqualTo("jwt-token");
        assertThat(response.getUser().getEmail()).isEqualTo("jane@ehps.com");
        assertThat(response.getUser().getRole()).isEqualTo("technician");
    }

    @Test
    void login_badCredentials_throws401() {
        LoginRequest request = new LoginRequest();
        request.setEmail("jane@ehps.com");
        request.setPassword("wrong-password");

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> authService().login(request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode().value()).isEqualTo(401);
                    assertThat(rse.getReason()).isEqualTo("Invalid email or password");
                });
    }

    @Test
    void login_userNotFoundAfterAuthentication_throws401() {
        LoginRequest request = new LoginRequest();
        request.setEmail("ghost@ehps.com");
        request.setPassword("Password@123");

        when(userRepository.findByEmail("ghost@ehps.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService().login(request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode().value()).isEqualTo(401);
                    assertThat(rse.getReason()).isEqualTo("Invalid email or password");
                });
    }
}
