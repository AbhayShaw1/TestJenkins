package com.app.ehps.checkup;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CheckupRepository extends JpaRepository<Checkup, Long> {
}
