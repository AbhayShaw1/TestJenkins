package com.app.ehps.machine;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MachineTypeParameterRepository extends JpaRepository<MachineTypeParameter, Long> {

    List<MachineTypeParameter> findByMachineType_TypeIdOrderByParamIndex(Long typeId);
}
