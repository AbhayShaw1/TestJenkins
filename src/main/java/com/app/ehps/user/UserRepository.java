package com.app.ehps.user;

import com.app.ehps.common.constant.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndEmpIdNot(String email, Long empId);

    List<User> findByRoleOrderByEmpId(Role role);

    Optional<User> findByEmpIdAndRole(Long empId, Role role);

    List<User> findByRoleAndSpecialityIgnoreCase(Role role, String speciality);
}
