package com.app.ehps.user;

import com.app.ehps.common.constant.Role;
import com.app.ehps.user.dto.AddTechnicianRequest;
import com.app.ehps.user.dto.TechnicianResponse;
import com.app.ehps.user.dto.UpdateTechnicianRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Field;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FabTechnicianServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private FabTechnicianService fabTechnicianService() {
        return new FabTechnicianService(userRepository, passwordEncoder);
    }

    private AddTechnicianRequest addRequest(String name, String email, String phone, String password, String speciality) {
        AddTechnicianRequest request = new AddTechnicianRequest();
        setField(request, "name", name);
        setField(request, "email", email);
        setField(request, "phone", phone);
        setField(request, "password", password);
        setField(request, "speciality", speciality);
        return request;
    }

    private UpdateTechnicianRequest updateRequest(String name, String email, String phone, String speciality) {
        UpdateTechnicianRequest request = new UpdateTechnicianRequest();
        setField(request, "name", name);
        setField(request, "email", email);
        setField(request, "phone", phone);
        setField(request, "speciality", speciality);
        return request;
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private User technician(Long empId, String name, String email, String phone, String speciality) {
        User user = new User();
        user.setEmpId(empId);
        user.setName(name);
        user.setEmail(email);
        user.setPhone(phone);
        user.setPassword("ENCODED_HASH");
        user.setRole(Role.TECHNICIAN);
        user.setSpeciality(speciality);
        return user;
    }

    @Test
    void addTechnician_success_savesEncodedPasswordTrimmedFieldsAndTechnicianRole() {
        AddTechnicianRequest request = addRequest("  Tech One  ", "tech1@ehps.com", "9876543210",
                "Password@123", "  lithography  ");

        when(userRepository.existsByEmailIgnoreCase("tech1@ehps.com")).thenReturn(false);
        when(passwordEncoder.encode("Password@123")).thenReturn("ENCODED_HASH");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setEmpId(10001L);
            return u;
        });

        TechnicianResponse response = fabTechnicianService().addTechnician(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();

        assertThat(saved.getName()).isEqualTo("Tech One");
        assertThat(saved.getEmail()).isEqualTo("tech1@ehps.com");
        assertThat(saved.getPhone()).isEqualTo("9876543210");
        assertThat(saved.getPassword()).isEqualTo("ENCODED_HASH");
        assertThat(saved.getRole()).isEqualTo(Role.TECHNICIAN);
        assertThat(saved.getSpeciality()).isEqualTo("lithography");

        assertThat(response.getTechnicianId()).isEqualTo(10001L);
        assertThat(response.getName()).isEqualTo("Tech One");
        assertThat(response.getEmail()).isEqualTo("tech1@ehps.com");
        assertThat(response.getRole()).isEqualTo("technician");
        assertThat(response.getSpeciality()).isEqualTo("lithography");
    }

    @Test
    void addTechnician_duplicateEmail_throws409() {
        AddTechnicianRequest request = addRequest("Tech One", "tech1@ehps.com", "9876543210",
                "Password@123", "lithography");

        when(userRepository.existsByEmailIgnoreCase("tech1@ehps.com")).thenReturn(true);

        assertThatThrownBy(() -> fabTechnicianService().addTechnician(request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode().value()).isEqualTo(409);
                    assertThat(rse.getReason()).isEqualTo("User already exists with email: tech1@ehps.com");
                });
    }

    @Test
    void getTechnicianById_invalidId_throws400() {
        FabTechnicianService service = fabTechnicianService();

        assertThatThrownBy(() -> service.getTechnicianById(null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode().value()).isEqualTo(400);
                    assertThat(rse.getReason()).isEqualTo("Invalid technician id");
                });

        assertThatThrownBy(() -> service.getTechnicianById(0L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode().value()).isEqualTo(400);
                });

        assertThatThrownBy(() -> service.getTechnicianById(-1L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode().value()).isEqualTo(400);
                });
    }

    @Test
    void getTechnicianById_notFound_throws404() {
        when(userRepository.findByEmpIdAndRole(10001L, Role.TECHNICIAN)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> fabTechnicianService().getTechnicianById(10001L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode().value()).isEqualTo(404);
                    assertThat(rse.getReason()).isEqualTo("Technician not found");
                });
    }

    @Test
    void updateTechnician_success_updatesFieldsButNotRoleOrPassword() {
        User existing = technician(10001L, "Old Name", "old@ehps.com", "9876543210", "lithography");

        UpdateTechnicianRequest request = updateRequest("  New Name  ", "new@ehps.com", "9876543211",
                "  etcher  ");

        when(userRepository.findByEmpIdAndRole(10001L, Role.TECHNICIAN)).thenReturn(Optional.of(existing));
        when(userRepository.existsByEmailIgnoreCaseAndEmpIdNot("new@ehps.com", 10001L)).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TechnicianResponse response = fabTechnicianService().updateTechnician(10001L, request);

        assertThat(response.getName()).isEqualTo("New Name");
        assertThat(response.getEmail()).isEqualTo("new@ehps.com");
        assertThat(response.getPhone()).isEqualTo("9876543211");
        assertThat(response.getSpeciality()).isEqualTo("etcher");
        assertThat(response.getRole()).isEqualTo("technician");

        assertThat(existing.getPassword()).isEqualTo("ENCODED_HASH");
        assertThat(existing.getRole()).isEqualTo(Role.TECHNICIAN);
    }

    @Test
    void updateTechnician_duplicateEmail_throws409() {
        User existing = technician(10001L, "Old Name", "old@ehps.com", "9876543210", "lithography");

        UpdateTechnicianRequest request = updateRequest("New Name", "taken@ehps.com", "9876543211", "etcher");

        when(userRepository.findByEmpIdAndRole(10001L, Role.TECHNICIAN)).thenReturn(Optional.of(existing));
        when(userRepository.existsByEmailIgnoreCaseAndEmpIdNot("taken@ehps.com", 10001L)).thenReturn(true);

        assertThatThrownBy(() -> fabTechnicianService().updateTechnician(10001L, request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode().value()).isEqualTo(409);
                    assertThat(rse.getReason()).isEqualTo("User already exists with email: taken@ehps.com");
                });
    }
}
