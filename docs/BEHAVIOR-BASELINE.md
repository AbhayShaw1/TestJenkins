# EHPS — Behavioral Baseline (invariants that MUST NOT change)

This document is the **source of truth** for the rebuild. Every rule below is extracted from the
legacy code (preserved on the `rebuild` branch baseline commit and on `frontend_connected`). The HTTP
surface is being modernized (see `API-CONTRACT.md`), but **all business semantics below must be
preserved exactly**. Any deviation is a regression.

---

## 1. Roles & authorization

Three roles. DB enum values are lowercase; Spring authorities are `ROLE_` + uppercase.

| DB enum (`user_role`) | Spring authority | URL prefix (legacy) |
|---|---|---|
| `manager` | `ROLE_MANAGER` | `/api/manager/**` |
| `fab_coordinator` | `ROLE_FAB_COORDINATOR` | `/api/fab/**` |
| `technician` | `ROLE_TECHNICIAN` | `/api/technician/**` |

- Public (no auth): `/api/auth/**`, `/swagger-ui/**`, `/swagger-ui.html`, `/v3/api-docs/**`, `/actuator/**`.
- Dashboard (`/api/dashboard/equipment`) is accessible to **both** `manager` and `fab_coordinator`.
- Session policy: **STATELESS**. CSRF disabled. BCrypt password encoder.
- Beyond URL-role checks, **services re-verify role and ownership** using the logged-in user's email
  (JWT subject). Two distinct failure modes must be preserved:
  - Wrong role reaching a service method → **403 FORBIDDEN** ("Only X can access this API").
  - Authenticated email not found in DB → **401 UNAUTHORIZED** ("User not found").
- Ownership scoping: fab coordinators only see/modify **their own** machines and **their own** alerts
  (`fab_id = loggedInUser.empId`); technicians only act on work/alerts **assigned to them**.

## 2. Registration (`AuthService.register`)

- **Validation (Bean Validation on request DTO):**
  - `name`: `@NotBlank` — "Name is required".
  - `email`: `@Pattern` `^[A-Za-z0-9._%+-]+@ehps\.com$` — "Only @ehps.com email addresses are allowed".
  - `phone`: exactly 10 digits — "Phone number must be exactly 10 digits".
  - `password`: `@Pattern` min 8 chars incl. uppercase, lowercase, digit, special `@$!%*?&` —
    "Password must be at least 8 characters long and include uppercase, lowercase, number, and special character".
  - `role`: `@NotNull` — "Role is required".
  - `speciality`: optional.
- Email uniqueness: `existsByEmailIgnoreCase` → **409 CONFLICT** "User already exists with email: {email}".
- Password stored **BCrypt**-encoded. All string fields **trimmed**. `speciality` trimmed if present.
- `empId` auto-generated (identity, starts 10000).
- Response: `success=true`, `message="Registration successful"`, `token=null`, `user={empId,name,email,phone,role,speciality}`.
- Legacy status: **202 ACCEPTED** → rebuild changes to **201 CREATED** (see API-CONTRACT; this is an intentional HTTP correction, body unchanged).

## 3. Login (`AuthService.login`)

- Authenticate via `AuthenticationManager` (`UsernamePasswordAuthenticationToken(email, password)`).
- Bad credentials OR user-not-found → **401 UNAUTHORIZED** "Invalid email or password" (both cases collapse to the same message — preserve this).
- Email trimmed for lookup. On success: issue JWT.
- Response: `success=true`, `message="Login successful"`, `token=<jwt>`, `user={...}`. Status **200**.

## 4. JWT (`JwtService`)

- Algorithm **HS256**. Secret is **BASE64-decoded** then used as HMAC-SHA key.
- Claims: `sub` = email, `role` = `UserRole.name()` (lowercase enum name), `empId` = user PK, `iat`, `exp`.
- Expiration **86_400_000 ms (24h)**.
- Validation: subject email matches `UserDetails.username` AND not expired.
- Filter skips: `/api/auth/register`, `/api/auth/login`, `/swagger-ui/**`, `/v3/api-docs**`. Missing/invalid
  bearer token → request proceeds unauthenticated (then entry point returns 401 on protected routes).
- `JwtAuthEntryPoint` on unauthenticated access to protected route → **401** JSON
  `{"success":false,"message":"Unauthorized. Please provide a valid JWT token."}`.

## 5. Error model (`ApiErrorResponse` + `GlobalExceptionHandler`)

Error body fields: `success`(false), `message`, `status`(int), `path`, `timestamp`, `errors`(Map<String,String>).

| Exception | HTTP | message | errors |
|---|---|---|---|
| `MethodArgumentNotValidException` | 400 | "Validation failed" | field → message |
| `HttpMessageNotReadableException` | 400 | "Invalid request body" | {} |
| `ResponseStatusException` | its status | its reason | {} |
| `AccessDeniedException` | 403 | "Access denied. You do not have permission." | {} |
| `Exception` (fallback) | 500 | "Something went wrong. Please try again." | {} |

## 6. Machine types (reference data, 6 fixed rows)

| type_id | type_name | code prefix | speciality (lowercase) | # params |
|---|---|---|---|---|
| 1 | Lithography | `LH` | `lithography` | 5 |
| 2 | Etcher | `EH` | `etcher` | 5 |
| 3 | CVD | `CVD` | `cvd` | 4 |
| 4 | Ion Implanter | `ION` | `ion_implanter` | 4 |
| 5 | CMP | `CMP` | `cmp` | 4 |
| 6 | Inspection | `INS` | `inspection` | 4 |

- **Machine code**: trimmed, **UPPERCASED** (`Locale.ROOT`); must **start with the type's prefix** else
  400 "Machine code must start with {prefix} for selected machine type"; unique case-insensitive
  (409 "Machine code already exists"; on update, excluding self).
- **Speciality matching**: technician `speciality` must equal the type's speciality (case-insensitive)
  for checkup/repair assignment.

## 7. Machine management (fab coordinator, ownership-scoped)

- `addMachine`: validate prefix + type exists (404 "Machine type not found") + uniqueness; owner = logged-in fab; 201.
- `getMyMachines`: `fab_id = me`, ordered by machineId.
- `getMachineById`: must belong to me (404 "Machine not found for this fab coordinator"); invalid id (null/≤0) → 400 "Invalid machine id".
- `updateMachine`: same validations; uniqueness excludes self.
- `machineId` auto-generated (identity, starts 1000).

## 8. Technician management (fab coordinator)

- `addTechnician`: role forced to `technician`; email unique (409); BCrypt password; `speciality` required + trimmed; all fields trimmed.
- `getAllTechnicians`: role=technician, ordered by empId.
- `getTechnicianById`: invalid id → 400; not found → 404 "Technician not found".
- `updateTechnician`: updates name/email/phone/speciality only (NOT role/password); email unique excluding self.

## 9. Checkup engine (`TechnicianCheckupService.performCheckup`) — CROWN JEWEL, preserve exactly

**Precondition:** machine must have an active (`completed=false`) `checkup` `TechnicianWork` assigned to the
logged-in technician (else 403 "Machine not assigned to this technician for checkup"). `machineId` invalid → 400.

**Input:** `values: List<Float>` — count must equal the type's param count (5 for types 1–2, 4 for types 3–6);
empty/null/mismatched → 400. All values non-null.

**Per-parameter status** (`good`/`warning`/`bad`) via type-specific thresholds:

**Type 1 Lithography (5):** P1 Light Intensity mW/cm²: good [80,100], warn [60,80), bad <60 · P2 Lens Temp °C: good [20,25], warn [26,28], bad >28 · P3 Stage Vibration nm: good <3, warn [3,5], bad >5 · P4 Reticle Alignment Err nm: good <2, warn [2,4], bad >4 · P5 Focus Accuracy nm: good <10, warn [10,20], bad >20.

**Type 2 Etcher (5):** P1 RF Plasma Power W: good [450,500], warn [400,450), bad <400 · P2 Chamber Pressure mTorr: good [20,30], warn [31,40], bad >40 · P3 Chamber Temp °C: good [60,80], warn [81,90], bad >90 · P4 Gas Flow Rate sccm: good [80,100], warn [60,80), bad <60 · P5 Etch Rate nm/min: good [100,120], warn [85,100), bad <85.

**Type 3 CVD (4):** P1 Chamber Temp °C: good [300,350], warn [351,370], bad >370 · P2 Gas Flow sccm: good [100,150], warn [80,100), bad <80 · P3 Vacuum Pressure Torr: good [0.5,1], warn (1,2], bad >2 · P4 Deposition Uniformity %: good >95, warn [90,95], bad <90.

**Type 4 Ion Implanter (4):** P1 Beam Current mA: good [10,12], warn [8,10), bad <8 · P2 Beam Energy keV: good [40,50], warn [30,40), bad <30 · P3 Vacuum Pressure Torr: good ≤0.000001, warn ≤0.00001, bad >0.0001 *(note the intentional gap between 0.00001 and 0.0001 — preserve exactly)* · P4 Cooling Temp °C: good [18,22], warn [23,25], bad >25.

**Type 5 CMP (4):** P1 Slurry Flow ml/min: good [150,180], warn [120,150), bad <120 · P2 Pad Pressure psi: good [3,5], warn (5,6], bad >6 · P3 Platen Speed RPM: good [60,80], warn [81,95], bad >95 · P4 Pad Temp °C: good [25,35], warn [36,40], bad >40.

**Type 6 Inspection (4):** P1 Laser Power %: good [90,100], warn [75,90), bad <75 · P2 Sensor Calibration nm: good [0,2], warn [3,5], bad >5 · P3 Focus Error nm: good [0,10], warn [11,20], bad >20 · P4 Defect Count: good <10, warn [10,50], bad >50.

> The exact boundary operators above (inclusive/exclusive, gaps) are the current behavior. Port them
> verbatim and pin every boundary with a unit test.

**Health score:** each param weighted equally — **20 pts** if 5 params, **25 pts** if 4 params. Contribution:
`good`=full weight, `warning`=half weight, `bad`=0. `final_health = round(sum of contributions)` (int).

**Risk alert auto-generation:** create alert iff **badCount > 0 OR warningCount > 3**.
- `severity` = `high` if badCount>0 else `medium`.
- `status` = `pending`; `approvedBy`=null, `assignedTechnician`=null; `fab_id` = machine's fab owner; `raised_on`=today.
- `problem_measure` = comma-separated list of non-good params: value + status (preserve legacy format).

**Persistence:** insert a checkup record for the machine's type (legacy: one of 6 tables; rebuild: unified
`checkups` + `checkup_readings`) storing each param value+status, `check_date`=today, `final_health`.
Then mark the assigned `TechnicianWork` `completed=true` **after** successful checkup.

**Parameter rules endpoint** returns the per-type parameter metadata (name, unit, good/warning/bad ranges);
invalid type → 400.

## 10. Alert state machine (statuses are lowercase strings)

```
pending ──escalate(fab)──▶ sent_to_manager ──approve(mgr)──▶ approved ──assign+complete──▶ resolved
                                           └──reject(mgr)───▶ rejected
```

- **Fab** `getPendingAlerts`: own + `status=pending`, ordered raisedOn desc.
- **Fab** `sendToManager`: own + `pending` → `sent_to_manager` (invalid id 400; not found 404).
- **Fab** `getApprovedUnassignedAlerts`: own + `approved` + `assignedTechnician IS NULL`.
- **Fab** `getMatchingTechnicians(alertId)`: alert approved+unassigned+own; techs whose speciality matches the machine type.
- **Fab** `assignRepairTechnician`: alert approved+unassigned+own; tech exists+role technician+speciality match;
  no duplicate repair work (same machine+tech+date) → 409; creates `TechnicianWork` `work_type=repair`; sets `assignedTechnician`.
- **Manager** `getAllAlerts`: status NOT IN (`pending`,`resolved`), ordered alertId desc.
- **Manager** `getPendingManagerApprovalAlerts`: `status=sent_to_manager`, ordered raisedOn desc.
- **Manager** `approveAlert`: `sent_to_manager` → `approved`, records `approvedBy=me` (404 if not in that status).
- **Manager** `rejectAlert`: `sent_to_manager` → `rejected`, records `approvedBy=me` (field reused for rejector).

## 11. Checkup assignment (`FabCheckupAssignmentService.assignTechnicianForCheckup`)

- Machine must have **no unresolved alerts**: `existsByMachine_MachineIdAndStatusNotIn([resolved,rejected])` → if true, 400.
- Machine exists (404), technician exists+role technician (else 400/404), speciality matches type.
- No duplicate active checkup (same machine + `checkup` + date + `completed=false`) → 409.
- Creates `TechnicianWork` `work_type=checkup`, `completed=false`, `fab_id`=logged-in fab.

## 12. Repairs (`TechnicianRepairService`)

- `getApprovedRepairAlerts`: `assignedTechnician=me` + `status=approved`, ordered raisedOn desc.
- `getApprovedRepairAlert(id)`: approved + assigned to me (invalid id 400; else 403).
- `getMachineDetails(id)`: any technician may view (invalid id/type missing 400; not found 404).
- `completeRepair`: alert approved + assigned to me; trims fields; creates **`Repair`** (machine,tech,alert,repair_date=today,changesDone,observations)
  AND **`MachineHistory`** (issue=alert.problemMeasure, repair_action=changesDone, observations, history_date=today);
  sets alert `status=resolved`. Response flags `repairRecorded=true`, `historyRecorded=true`.

## 13. Technician account

- `changePassword`: current password must match (BCrypt); new must differ from current; new stored BCrypt-encoded. Null/mismatch/same → 400.

## 14. Equipment dashboard (`EquipmentDashboardService.getDashboardHistory`)

- Roles manager or fab_coordinator (else 403; user-not-found 401).
- `fromDate`, `toDate` required (400 each if missing); `fromDate > toDate` → 400; `typeId` null/<0 → 400.
- `typeId=0` → all types; `typeId>0` → filter by machine type. Ordered by historyDate desc.

## 15. Identity ranges & seed (dev/test)

- `users.emp_id` starts 10000; `machine_types.type_id` starts 1; `machines.machine_id` starts 1000; other tables from 1.
- Seed users: manager1@ (manager), fab@ (fab_coordinator), tech1@ (technician, Lithography), tech2@ (technician, Etcher).
  *(Note: legacy seed uses @test.com emails and plaintext seed passwords; rebuild dev seed should use BCrypt hashes and, where these accounts must log in via the @ehps.com rule, valid emails — call out any change explicitly.)*
