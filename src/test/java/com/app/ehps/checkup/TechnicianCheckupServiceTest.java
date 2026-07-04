package com.app.ehps.checkup;

import com.app.ehps.alert.RiskAlert;
import com.app.ehps.alert.RiskAlertRepository;
import com.app.ehps.checkup.dto.MachineDetailsResponse;
import com.app.ehps.checkup.dto.PerformCheckupRequest;
import com.app.ehps.checkup.dto.PerformCheckupResult;
import com.app.ehps.checkup.engine.CheckupEngine;
import com.app.ehps.common.constant.Role;
import com.app.ehps.machine.Machine;
import com.app.ehps.machine.MachineRepository;
import com.app.ehps.machine.MachineType;
import com.app.ehps.machine.MachineTypeParameter;
import com.app.ehps.machine.MachineTypeParameterRepository;
import com.app.ehps.user.User;
import com.app.ehps.user.UserRepository;
import com.app.ehps.work.TechnicianWork;
import com.app.ehps.work.TechnicianWorkRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link TechnicianCheckupService}, mirroring legacy
 * {@code TechnicianCheckupService.performCheckup} behavior (BEHAVIOR-BASELINE.md §9). Uses the
 * REAL {@link CheckupEngine} (no mocking) so scoring is exercised end-to-end; only repositories
 * are mocked.
 */
@ExtendWith(MockitoExtension.class)
class TechnicianCheckupServiceTest {

    private static final String TECH_EMAIL = "tech1@ehps.com";
    private static final Long TECH_EMP_ID = 10001L;
    private static final Long MACHINE_ID = 1000L;
    private static final Long WORK_ID = 1L;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TechnicianWorkRepository technicianWorkRepository;

    @Mock
    private MachineRepository machineRepository;

    @Mock
    private MachineTypeParameterRepository machineTypeParameterRepository;

    @Mock
    private CheckupRepository checkupRepository;

    @Mock
    private RiskAlertRepository riskAlertRepository;

    private final CheckupEngine checkupEngine = new CheckupEngine();

    private TechnicianCheckupService service() {
        return new TechnicianCheckupService(userRepository, technicianWorkRepository, machineRepository,
                machineTypeParameterRepository, checkupRepository, riskAlertRepository, checkupEngine);
    }

    @BeforeEach
    void seedSecurityContext() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(TECH_EMAIL, null));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private User technician() {
        User user = new User();
        user.setEmpId(TECH_EMP_ID);
        user.setName("Tech One");
        user.setEmail(TECH_EMAIL);
        user.setPassword("ENCODED_HASH");
        user.setRole(Role.TECHNICIAN);
        user.setSpeciality("lithography");
        return user;
    }

    private User fabUser() {
        User user = new User();
        user.setEmpId(20000L);
        user.setName("Fab One");
        user.setEmail("fab@ehps.com");
        user.setPassword("ENCODED_HASH");
        user.setRole(Role.FAB_COORDINATOR);
        return user;
    }

    private MachineType lithographyType() {
        MachineType type = new MachineType();
        type.setTypeId(1L);
        type.setTypeName("Lithography");
        type.setCodePrefix("LH");
        type.setSpeciality("lithography");
        type.setParamCount(5);
        return type;
    }

    private Machine machine() {
        Machine machine = new Machine();
        machine.setMachineId(MACHINE_ID);
        machine.setMachineCode("LH-700");
        machine.setMachineType(lithographyType());
        machine.setFabUser(fabUser());
        return machine;
    }

    private TechnicianWork work() {
        TechnicianWork work = new TechnicianWork();
        work.setWorkId(WORK_ID);
        work.setMachine(machine());
        work.setTechnician(technician());
        work.setFabUser(fabUser());
        work.setWorkType("checkup");
        work.setCompleted(false);
        return work;
    }

    private List<MachineTypeParameter> lithographyParams() {
        List<MachineTypeParameter> params = new ArrayList<>();
        params.add(param(1, "Light Intensity", "mW/cm²"));
        params.add(param(2, "Lens Temperature", "°C"));
        params.add(param(3, "Stage Vibration", "nm"));
        params.add(param(4, "Reticle Alignment Error", "nm"));
        params.add(param(5, "Focus Accuracy", "nm"));
        return params;
    }

    private MachineTypeParameter param(int index, String name, String unit) {
        MachineTypeParameter p = new MachineTypeParameter();
        p.setParamIndex(index);
        p.setParamName(name);
        p.setUnit(unit);
        return p;
    }

    private PerformCheckupRequest requestWithValues(Float... values) {
        PerformCheckupRequest request = new PerformCheckupRequest();
        request.setValues(Arrays.asList(values));
        return request;
    }

    // ---- performCheckup happy path ----

    @Test
    void performCheckup_allGoodValues_returnsFullHealthNoAlertAndMarksWorkCompleted() {
        when(userRepository.findByEmail(TECH_EMAIL)).thenReturn(Optional.of(technician()));
        when(technicianWorkRepository
                .findFirstByTechnician_EmpIdAndMachine_MachineIdAndWorkTypeIgnoreCaseAndCompletedFalseOrderByWorkIdDesc(
                        TECH_EMP_ID, MACHINE_ID, "checkup"))
                .thenReturn(Optional.of(work()));
        when(technicianWorkRepository
                .existsByTechnician_EmpIdAndMachine_MachineIdAndWorkTypeIgnoreCaseAndCompletedFalse(
                        TECH_EMP_ID, MACHINE_ID, "checkup"))
                .thenReturn(true);
        when(machineRepository.findById(MACHINE_ID)).thenReturn(Optional.of(machine()));
        when(checkupRepository.save(any(Checkup.class))).thenAnswer(inv -> inv.getArgument(0));

        // All-good lithography values: intensity 90, lens temp 22, vibration 1, alignment 1, focus 5
        PerformCheckupRequest request = requestWithValues(90f, 22f, 1f, 1f, 5f);

        PerformCheckupResult result = service().performCheckup(MACHINE_ID, request);

        assertThat(result.getFinalHealth()).isEqualTo(100);
        assertThat(result.isRiskAlertCreated()).isFalse();
        assertThat(result.getRiskAlertId()).isNull();
        assertThat(result.getSeverity()).isNull();
        assertThat(result.getStatuses()).containsExactly("good", "good", "good", "good", "good");
        assertThat(result.getMachineId()).isEqualTo(MACHINE_ID);
        assertThat(result.getMachineType()).isEqualTo("Lithography");

        ArgumentCaptor<Checkup> checkupCaptor = ArgumentCaptor.forClass(Checkup.class);
        verify(checkupRepository).save(checkupCaptor.capture());
        Checkup savedCheckup = checkupCaptor.getValue();
        assertThat(savedCheckup.getFinalHealth()).isEqualTo(100);
        assertThat(savedCheckup.getReadings()).hasSize(5);

        verify(riskAlertRepository, never()).save(any(RiskAlert.class));

        ArgumentCaptor<TechnicianWork> workCaptor = ArgumentCaptor.forClass(TechnicianWork.class);
        verify(technicianWorkRepository).save(workCaptor.capture());
        assertThat(workCaptor.getValue().getCompleted()).isTrue();
    }

    // ---- performCheckup bad-value path triggers alert ----

    @Test
    void performCheckup_badValue_createsHighSeverityAlertWithProblemMeasure() {
        when(userRepository.findByEmail(TECH_EMAIL)).thenReturn(Optional.of(technician()));
        when(technicianWorkRepository
                .findFirstByTechnician_EmpIdAndMachine_MachineIdAndWorkTypeIgnoreCaseAndCompletedFalseOrderByWorkIdDesc(
                        TECH_EMP_ID, MACHINE_ID, "checkup"))
                .thenReturn(Optional.of(work()));
        when(technicianWorkRepository
                .existsByTechnician_EmpIdAndMachine_MachineIdAndWorkTypeIgnoreCaseAndCompletedFalse(
                        TECH_EMP_ID, MACHINE_ID, "checkup"))
                .thenReturn(true);
        when(machineRepository.findById(MACHINE_ID)).thenReturn(Optional.of(machine()));
        when(checkupRepository.save(any(Checkup.class))).thenAnswer(inv -> inv.getArgument(0));
        when(machineTypeParameterRepository.findByMachineType_TypeIdOrderByParamIndex(1L))
                .thenReturn(lithographyParams());
        when(riskAlertRepository.save(any(RiskAlert.class))).thenAnswer(inv -> {
            RiskAlert alert = inv.getArgument(0);
            alert.setAlertId(500L);
            return alert;
        });

        // P1 = 40 -> bad (light intensity < 60); rest good.
        PerformCheckupRequest request = requestWithValues(40f, 22f, 1f, 1f, 5f);

        PerformCheckupResult result = service().performCheckup(MACHINE_ID, request);

        assertThat(result.isRiskAlertCreated()).isTrue();
        assertThat(result.getRiskAlertId()).isEqualTo(500L);
        assertThat(result.getSeverity()).isEqualTo("high");
        assertThat(result.getStatuses().get(0)).isEqualTo("bad");

        ArgumentCaptor<RiskAlert> alertCaptor = ArgumentCaptor.forClass(RiskAlert.class);
        verify(riskAlertRepository).save(alertCaptor.capture());
        RiskAlert savedAlert = alertCaptor.getValue();

        assertThat(savedAlert.getStatus()).isEqualTo("pending");
        assertThat(savedAlert.getSeverity()).isEqualTo("high");
        assertThat(savedAlert.getProblemMeasure()).isNotEmpty();
        assertThat(savedAlert.getProblemMeasure()).isEqualTo("Light Intensity: 40.0 mW/cm² (bad)");
        assertThat(savedAlert.getApprovedBy()).isNull();
        assertThat(savedAlert.getAssignedTechnician()).isNull();
        assertThat(savedAlert.getFabUser().getEmpId()).isEqualTo(machine().getFabUser().getEmpId());

        verify(technicianWorkRepository).save(any(TechnicianWork.class));
    }

    // ---- not assigned -> 403 ----

    @Test
    void performCheckup_notAssigned_throws403() {
        when(userRepository.findByEmail(TECH_EMAIL)).thenReturn(Optional.of(technician()));
        when(technicianWorkRepository
                .findFirstByTechnician_EmpIdAndMachine_MachineIdAndWorkTypeIgnoreCaseAndCompletedFalseOrderByWorkIdDesc(
                        TECH_EMP_ID, MACHINE_ID, "checkup"))
                .thenReturn(Optional.empty());

        PerformCheckupRequest request = requestWithValues(90f, 22f, 1f, 1f, 5f);

        assertThatThrownBy(() -> service().performCheckup(MACHINE_ID, request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode().value()).isEqualTo(403);
                    assertThat(rse.getReason()).isEqualTo("Machine is not assigned to this technician for checkup");
                });

        verify(checkupRepository, never()).save(any(Checkup.class));
        verify(technicianWorkRepository, never()).save(any(TechnicianWork.class));
    }

    // ---- insufficient values -> 400 ----

    @Test
    void performCheckup_insufficientValues_throws400() {
        when(userRepository.findByEmail(TECH_EMAIL)).thenReturn(Optional.of(technician()));
        when(technicianWorkRepository
                .findFirstByTechnician_EmpIdAndMachine_MachineIdAndWorkTypeIgnoreCaseAndCompletedFalseOrderByWorkIdDesc(
                        TECH_EMP_ID, MACHINE_ID, "checkup"))
                .thenReturn(Optional.of(work()));
        when(technicianWorkRepository
                .existsByTechnician_EmpIdAndMachine_MachineIdAndWorkTypeIgnoreCaseAndCompletedFalse(
                        TECH_EMP_ID, MACHINE_ID, "checkup"))
                .thenReturn(true);
        when(machineRepository.findById(MACHINE_ID)).thenReturn(Optional.of(machine()));

        // Only 3 values, but lithography (type 1) needs 5.
        PerformCheckupRequest request = requestWithValues(90f, 22f, 1f);

        assertThatThrownBy(() -> service().performCheckup(MACHINE_ID, request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode().value()).isEqualTo(400);
                    assertThat(rse.getReason()).isEqualTo("Insufficient parameter values");
                });

        verify(checkupRepository, never()).save(any(Checkup.class));
    }

    // ---- null value -> 400 ----

    @Test
    void performCheckup_nullValueAtPosition_throws400() {
        when(userRepository.findByEmail(TECH_EMAIL)).thenReturn(Optional.of(technician()));
        when(technicianWorkRepository
                .findFirstByTechnician_EmpIdAndMachine_MachineIdAndWorkTypeIgnoreCaseAndCompletedFalseOrderByWorkIdDesc(
                        TECH_EMP_ID, MACHINE_ID, "checkup"))
                .thenReturn(Optional.of(work()));
        when(technicianWorkRepository
                .existsByTechnician_EmpIdAndMachine_MachineIdAndWorkTypeIgnoreCaseAndCompletedFalse(
                        TECH_EMP_ID, MACHINE_ID, "checkup"))
                .thenReturn(true);
        when(machineRepository.findById(MACHINE_ID)).thenReturn(Optional.of(machine()));

        PerformCheckupRequest request = requestWithValues(90f, null, 1f, 1f, 5f);

        assertThatThrownBy(() -> service().performCheckup(MACHINE_ID, request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode().value()).isEqualTo(400);
                    assertThat(rse.getReason()).isEqualTo("Parameter value at position 2 is required");
                });

        verify(checkupRepository, never()).save(any(Checkup.class));
    }

    // ---- invalid machine id -> 400 ----

    @Test
    void performCheckup_invalidMachineId_throws400() {
        assertThatThrownBy(() -> service().performCheckup(0L, requestWithValues(90f)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode().value()).isEqualTo(400);
                    assertThat(rse.getReason()).isEqualTo("Invalid machine id");
                });
    }

    // ---- getMachineDetails ----

    @Test
    void getMachineDetails_notAssigned_throws403() {
        when(userRepository.findByEmail(TECH_EMAIL)).thenReturn(Optional.of(technician()));
        when(technicianWorkRepository
                .existsByTechnician_EmpIdAndMachine_MachineIdAndWorkTypeIgnoreCaseAndCompletedFalse(
                        TECH_EMP_ID, MACHINE_ID, "checkup"))
                .thenReturn(false);

        assertThatThrownBy(() -> service().getMachineDetails(MACHINE_ID))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode().value()).isEqualTo(403);
                    assertThat(rse.getReason()).isEqualTo("Machine is not assigned to this technician for checkup");
                });
    }

    @Test
    void getMachineDetails_success_returnsMachineDetails() {
        when(userRepository.findByEmail(TECH_EMAIL)).thenReturn(Optional.of(technician()));
        when(technicianWorkRepository
                .existsByTechnician_EmpIdAndMachine_MachineIdAndWorkTypeIgnoreCaseAndCompletedFalse(
                        TECH_EMP_ID, MACHINE_ID, "checkup"))
                .thenReturn(true);
        when(machineRepository.findById(MACHINE_ID)).thenReturn(Optional.of(machine()));

        MachineDetailsResponse response = service().getMachineDetails(MACHINE_ID);

        assertThat(response.getMachineId()).isEqualTo(MACHINE_ID);
        assertThat(response.getMachineCode()).isEqualTo("LH-700");
        assertThat(response.getTypeId()).isEqualTo(1L);
        assertThat(response.getTypeName()).isEqualTo("Lithography");
    }

    // ---- getAssignedCheckupWorks ----

    @Test
    void getAssignedCheckupWorks_mapsWorksToResponses() {
        when(userRepository.findByEmail(TECH_EMAIL)).thenReturn(Optional.of(technician()));
        when(technicianWorkRepository.findByTechnician_EmpIdAndWorkTypeIgnoreCaseAndCompletedFalse(TECH_EMP_ID, "checkup"))
                .thenReturn(List.of(work()));

        List<?> results = service().getAssignedCheckupWorks();

        assertThat(results).hasSize(1);
    }
}
