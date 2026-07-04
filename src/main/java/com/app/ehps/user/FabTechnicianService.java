package com.app.ehps.user;

import com.app.ehps.common.constant.Role;
import com.app.ehps.user.dto.AddTechnicianRequest;
import com.app.ehps.user.dto.TechnicianResponse;
import com.app.ehps.user.dto.UpdateTechnicianRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Technician management for fab coordinators (BEHAVIOR-BASELINE.md §8).
 */
@Service
@Transactional
public class FabTechnicianService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public FabTechnicianService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public TechnicianResponse addTechnician(AddTechnicianRequest request) {
        String email = request.getEmail().trim();

        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "User already exists with email: " + email
            );
        }

        User technician = new User();
        technician.setName(request.getName().trim());
        technician.setEmail(email);
        technician.setPhone(request.getPhone() == null ? null : request.getPhone().trim());
        technician.setPassword(passwordEncoder.encode(request.getPassword()));
        technician.setRole(Role.TECHNICIAN);
        technician.setSpeciality(request.getSpeciality() == null ? null : request.getSpeciality().trim());

        User saved = userRepository.save(technician);

        return mapToTechnicianResponse(saved);
    }

    public List<TechnicianResponse> getAllTechnicians() {
        return userRepository.findByRoleOrderByEmpId(Role.TECHNICIAN).stream()
                .map(this::mapToTechnicianResponse)
                .collect(Collectors.toList());
    }

    public TechnicianResponse getTechnicianById(Long technicianId) {
        validateTechnicianId(technicianId);

        User technician = userRepository.findByEmpIdAndRole(technicianId, Role.TECHNICIAN)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Technician not found"));

        return mapToTechnicianResponse(technician);
    }

    public TechnicianResponse updateTechnician(Long technicianId, UpdateTechnicianRequest request) {
        validateTechnicianId(technicianId);

        User technician = userRepository.findByEmpIdAndRole(technicianId, Role.TECHNICIAN)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Technician not found"));

        String email = request.getEmail().trim();

        if (userRepository.existsByEmailIgnoreCaseAndEmpIdNot(email, technicianId)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "User already exists with email: " + email
            );
        }

        technician.setName(request.getName().trim());
        technician.setEmail(email);
        technician.setPhone(request.getPhone() == null ? null : request.getPhone().trim());
        technician.setSpeciality(request.getSpeciality() == null ? null : request.getSpeciality().trim());

        User saved = userRepository.save(technician);

        return mapToTechnicianResponse(saved);
    }

    private void validateTechnicianId(Long technicianId) {
        if (technicianId == null || technicianId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid technician id");
        }
    }

    private TechnicianResponse mapToTechnicianResponse(User user) {
        return new TechnicianResponse(
                user.getEmpId(),
                user.getName(),
                user.getEmail(),
                user.getPhone(),
                user.getRole().getDbValue(),
                user.getSpeciality()
        );
    }
}
