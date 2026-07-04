package com.app.ehps.checkup;

import com.app.ehps.alert.RiskAlert;
import com.app.ehps.alert.RiskAlertRepository;
import com.app.ehps.checkup.dto.AssignedWorkResponse;
import com.app.ehps.checkup.dto.MachineDetailsResponse;
import com.app.ehps.checkup.dto.PerformCheckupRequest;
import com.app.ehps.checkup.dto.PerformCheckupResult;
import com.app.ehps.checkup.engine.CheckupEngine;
import com.app.ehps.checkup.engine.CheckupEvaluation;
import com.app.ehps.common.security.SecurityUtils;
import com.app.ehps.machine.Machine;
import com.app.ehps.machine.MachineRepository;
import com.app.ehps.machine.MachineTypeParameter;
import com.app.ehps.machine.MachineTypeParameterRepository;
import com.app.ehps.user.User;
import com.app.ehps.user.UserRepository;
import com.app.ehps.work.TechnicianWork;
import com.app.ehps.work.TechnicianWorkRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Technician-only checkup business logic — verbatim port of legacy
 * {@code com.app.ehps_api.service.TechnicianCheckupService} (BEHAVIOR-BASELINE.md §9). Scoring
 * itself lives in {@link CheckupEngine} (the CROWN JEWEL); this service handles assignment
 * lookups, machine ownership checks, persistence, and risk-alert auto-generation.
 */
@Service
@Transactional
public class TechnicianCheckupService {

    private static final String WORK_TYPE_CHECKUP = "checkup";

    private final UserRepository userRepository;
    private final TechnicianWorkRepository technicianWorkRepository;
    private final MachineRepository machineRepository;
    private final MachineTypeParameterRepository machineTypeParameterRepository;
    private final CheckupRepository checkupRepository;
    private final RiskAlertRepository riskAlertRepository;
    private final CheckupEngine checkupEngine;

    public TechnicianCheckupService(UserRepository userRepository,
                                     TechnicianWorkRepository technicianWorkRepository,
                                     MachineRepository machineRepository,
                                     MachineTypeParameterRepository machineTypeParameterRepository,
                                     CheckupRepository checkupRepository,
                                     RiskAlertRepository riskAlertRepository,
                                     CheckupEngine checkupEngine) {
        this.userRepository = userRepository;
        this.technicianWorkRepository = technicianWorkRepository;
        this.machineRepository = machineRepository;
        this.machineTypeParameterRepository = machineTypeParameterRepository;
        this.checkupRepository = checkupRepository;
        this.riskAlertRepository = riskAlertRepository;
        this.checkupEngine = checkupEngine;
    }

    public List<AssignedWorkResponse> getAssignedCheckupWorks() {
        User technician = getLoggedInTechnician();

        List<TechnicianWork> works = technicianWorkRepository
                .findByTechnician_EmpIdAndWorkTypeIgnoreCaseAndCompletedFalse(
                        technician.getEmpId(),
                        WORK_TYPE_CHECKUP
                );

        List<AssignedWorkResponse> responseList = new ArrayList<>();

        for (TechnicianWork work : works) {
            Machine machine = work.getMachine();

            responseList.add(new AssignedWorkResponse(
                    work.getWorkId(),
                    machine != null ? machine.getMachineId() : null,
                    machine != null ? machine.getMachineCode() : null,
                    work.getFabUser() != null ? work.getFabUser().getEmpId() : null,
                    work.getWorkType(),
                    work.getWorkDate()
            ));
        }

        return responseList;
    }

    public MachineDetailsResponse getMachineDetails(Long machineId) {
        validateMachineId(machineId);

        User technician = getLoggedInTechnician();

        validateMachineAssignedToTechnician(technician.getEmpId(), machineId);

        Machine machine = getMachineById(machineId);

        return new MachineDetailsResponse(
                machine.getMachineId(),
                machine.getMachineCode(),
                machine.getMachineType().getTypeId(),
                machine.getMachineType().getTypeName(),
                machine.getInstallDate(),
                machine.getFabUser() != null ? machine.getFabUser().getEmpId() : null
        );
    }

    public PerformCheckupResult performCheckup(Long machineId, PerformCheckupRequest request) {
        validateMachineId(machineId);

        User technician = getLoggedInTechnician();

        TechnicianWork work = technicianWorkRepository
                .findFirstByTechnician_EmpIdAndMachine_MachineIdAndWorkTypeIgnoreCaseAndCompletedFalseOrderByWorkIdDesc(
                        technician.getEmpId(),
                        machineId,
                        WORK_TYPE_CHECKUP
                )
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Machine is not assigned to this technician for checkup"
                ));

        validateMachineAssignedToTechnician(technician.getEmpId(), machineId);

        Machine machine = getMachineById(machineId);

        int typeId = machine.getMachineType().getTypeId().intValue();

        int count = checkupEngine.parameterCount(typeId);

        if (count == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid machine type");
        }

        if (request == null || request.getValues() == null || request.getValues().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Parameter values are required");
        }

        if (request.getValues().size() < count) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Insufficient parameter values");
        }

        float[] values = new float[count];

        for (int i = 0; i < count; i++) {
            if (request.getValues().get(i) == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Parameter value at position " + (i + 1) + " is required"
                );
            }

            values[i] = request.getValues().get(i);
        }

        CheckupEvaluation eval = checkupEngine.evaluate(typeId, values);
        String[] statuses = eval.getStatuses();

        Checkup checkup = new Checkup();
        checkup.setMachine(machine);
        checkup.setTechnician(technician);
        checkup.setCheckDate(LocalDate.now());
        checkup.setFinalHealth(eval.getFinalHealth());

        for (int i = 0; i < count; i++) {
            CheckupReading reading = new CheckupReading();
            reading.setParamIndex(i + 1);
            reading.setReadingValue(values[i]);
            reading.setStatus(statuses[i]);
            checkup.addReading(reading);
        }

        checkupRepository.save(checkup);

        boolean riskAlertCreated = false;
        Long riskAlertId = null;
        String severity = null;

        if (eval.isAlertNeeded()) {
            String problemMeasure = buildProblemMeasure(typeId, values, statuses);

            severity = eval.getSeverity();

            RiskAlert alert = new RiskAlert();
            alert.setMachine(machine);
            alert.setProblemMeasure(problemMeasure);
            alert.setSeverity(severity);
            alert.setStatus("pending");
            alert.setRaisedOn(LocalDate.now());
            alert.setFabUser(machine.getFabUser());
            alert.setApprovedBy(null);
            alert.setAssignedTechnician(null);

            RiskAlert savedAlert = riskAlertRepository.save(alert);

            riskAlertCreated = true;
            riskAlertId = savedAlert.getAlertId();
        }

        work.setCompleted(true);
        technicianWorkRepository.save(work);

        return new PerformCheckupResult(
                machine.getMachineId(),
                machine.getMachineType().getTypeName(),
                eval.getFinalHealth(),
                List.of(statuses),
                riskAlertCreated,
                riskAlertId,
                severity
        );
    }

    private User getLoggedInTechnician() {
        return userRepository.findByEmail(SecurityUtils.currentUserEmail())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
    }

    private void validateMachineId(Long machineId) {
        if (machineId == null || machineId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid machine id");
        }
    }

    private void validateMachineAssignedToTechnician(Long technicianId, Long machineId) {
        boolean assigned = technicianWorkRepository
                .existsByTechnician_EmpIdAndMachine_MachineIdAndWorkTypeIgnoreCaseAndCompletedFalse(
                        technicianId,
                        machineId,
                        WORK_TYPE_CHECKUP
                );

        if (!assigned) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Machine is not assigned to this technician for checkup"
            );
        }
    }

    private Machine getMachineById(Long machineId) {
        Machine machine = machineRepository.findById(machineId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Machine not found"));

        if (machine.getMachineType() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Machine type not found for selected machine"
            );
        }

        return machine;
    }

    /**
     * PORT of legacy {@code buildProblemMeasure}: comma-separated list of non-good params in the
     * exact legacy format {@code "<paramName>: <value> <unit> (<status>)"}.
     */
    private String buildProblemMeasure(int typeId, float[] values, String[] statuses) {
        List<MachineTypeParameter> params = machineTypeParameterRepository
                .findByMachineType_TypeIdOrderByParamIndex((long) typeId);

        String problem = "";

        for (int i = 0; i < statuses.length; i++) {
            if (!"good".equals(statuses[i])) {
                MachineTypeParameter param = params.get(i);

                String oneProblem = param.getParamName() + ": " + values[i] + " " +
                        param.getUnit() + " (" + statuses[i] + ")";

                if (problem.isEmpty()) {
                    problem = oneProblem;
                } else {
                    problem = problem + ", " + oneProblem;
                }
            }
        }

        return problem;
    }
}
