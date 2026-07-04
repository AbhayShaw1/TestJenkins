# EHPS Backend

**EHPS** (Equipment Health & Performance System) is a REST API for maintaining
semiconductor-fab equipment. It coordinates three roles — **Manager**, **Fab Coordinator**,
and **Technician** — through an alert-driven maintenance workflow: technicians take sensor
readings, a scoring engine decides whether a machine is at risk, and a chain of
escalation/approval/repair steps drives the alert to resolution.

This is a ground-up **Spring Boot rebuild** of a legacy application (`com.app.ehps_api`),
built alongside the untouched legacy code under the new package `com.app.ehps` (strangler-fig
pattern). It preserves 100% of the legacy business behavior while correcting the REST contract.
See:

- [`docs/BEHAVIOR-BASELINE.md`](docs/BEHAVIOR-BASELINE.md) — the frozen business-behavior spec.
- [`docs/API-CONTRACT.md`](docs/API-CONTRACT.md) — the frozen target HTTP contract.
- [`docs/REBUILD-CONVENTIONS.md`](docs/REBUILD-CONVENTIONS.md) — engineering conventions for the rebuild.

---

## Table of contents

1. [Overview](#1-overview)
2. [The maintenance workflow](#2-the-maintenance-workflow)
3. [Tech stack](#3-tech-stack)
4. [Architecture](#4-architecture)
5. [Configuration & profiles](#5-configuration--profiles)
6. [Running locally](#6-running-locally)
7. [API documentation](#7-api-documentation)
8. [Testing](#8-testing)
9. [Project background](#9-project-background)

---

## 1. Overview

EHPS tracks the health of fab equipment (lithography scanners, etchers, CVD chambers, ion
implanters, CMP tools, inspection stations) and manages the workflow that keeps them running:

| Role | Responsibilities |
|---|---|
| **Fab Coordinator** | Registers machines and technicians for their fab, assigns checkup work, escalates risk alerts to management, and (once approved) assigns a speciality-matched technician to repair the machine. |
| **Technician** | Performs assigned checkups (submits sensor readings), and performs assigned repairs (records what was fixed). Scoped only to their own assigned work. |
| **Manager** | Reviews escalated risk alerts and approves or rejects them before repair work can proceed. Also has read visibility across all machines/technicians and the equipment dashboard. |

Every machine belongs to one of six fixed **machine types**, each with its own set of sensor
parameters, thresholds, and a required technician **speciality** for checkup/repair assignment
(e.g. a `lithography` machine can only be checked up or repaired by a technician whose
speciality is `lithography`).

## 2. The maintenance workflow

The core loop is: **assign → measure → score → escalate → approve → repair → resolve**.

```
 Fab Coordinator                Technician                 Checkup Engine            Manager
 ───────────────                ──────────                 ──────────────            ───────
 1. Register machine
 2. Assign checkup ─────────────▶
                                 3. Submit sensor
                                    readings ───────────────▶
                                                             4. Score each param
                                                                (good/warning/bad),
                                                                compute final_health
                                                             5. bad > 0 OR warning > 3
                                                                 └─▶ auto-create RiskAlert
                                                                     (status = pending)
 6. Escalate alert
    (pending → sent_to_manager) ──────────────────────────────────────────────────────▶
                                                                                        7. Approve or reject
                                                                                           (sent_to_manager →
                                                                                            approved | rejected)
 8. Assign speciality-matched
    repair technician (only if
    approved + unassigned) ───────────▶
                                 9. Complete repair:
                                    writes Repair +
                                    MachineHistory,
                                    alert → resolved
```

**Alert state machine** (statuses are lowercase strings, persisted verbatim):

```
pending ──escalate (fab)──▶ sent_to_manager ──approve (manager)──▶ approved ──assign + complete repair──▶ resolved
                                              └──reject (manager)──▶ rejected
```

Step by step:

1. **Register & assign** — a Fab Coordinator adds machines to their fab and assigns a
   checkup (`TechnicianWork`, `work_type=checkup`) to a technician whose speciality matches
   the machine type.
2. **Submit readings** — the assigned technician submits one float value per parameter for
   the machine's type (4 or 5 values, depending on type).
3. **Score & auto-raise** — the `CheckupEngine` classifies each parameter as `good` / `warning`
   / `bad` against type-specific thresholds, computes a weighted `final_health` score, and
   persists a `Checkup` + its `CheckupReading`s. If **`badCount > 0` OR `warningCount > 3`**,
   a `RiskAlert` is automatically created with `status=pending` and `severity=high` (any bad
   reading) or `medium` (warning-only). The assigned checkup work is marked complete.
4. **Escalate** — the Fab Coordinator sends a pending alert to management
   (`pending → sent_to_manager`).
5. **Approve / reject** — a Manager reviews and either approves (`→ approved`) or rejects
   (`→ rejected`) the alert, recording themselves as the approver/rejector.
6. **Assign repair** — for an approved, unassigned alert, the Fab Coordinator assigns a
   speciality-matched technician, creating a `TechnicianWork` with `work_type=repair`.
7. **Complete repair** — the assigned technician completes the repair, which writes a
   `Repair` record (what was changed, observations) and a `MachineHistory` record (issue,
   repair action, observations), and finally moves the alert to `resolved`.

## 3. Tech stack

| Concern | Choice |
|---|---|
| Language / runtime | Java 17 |
| Framework | Spring Boot 3.5 (`spring-boot-starter-web`, `-data-jpa`, `-security`, `-validation`, `-actuator`) |
| Database (local/prod) | PostgreSQL 16 |
| Database (tests) | H2, running in PostgreSQL compatibility mode |
| Auth | JWT via `jjwt` (api/impl/jackson 0.12.7), Spring Security, BCrypt |
| API docs | springdoc-openapi (`springdoc-openapi-starter-webmvc-ui`) |
| Boilerplate reduction | Lombok |
| Test frameworks | JUnit 5, Mockito, Spring Boot Test (MockMvc), AssertJ |

**Deliberate constraint:** no dependency beyond what's already declared in `pom.xml` may be
added for this rebuild. See `docs/REBUILD-CONVENTIONS.md` for the full allowed list.

## 4. Architecture

### Package layout

The rebuild lives entirely under the new base package **`com.app.ehps`** (note: no `_api`),
with its own Spring Boot entry point (`com.app.ehps.EhpsApplication`), so component scanning
never touches the legacy `com.app.ehps_api` application — the two apps are runtime-isolated
and coexist in the same repository during the migration.

Packaging is **feature-first**, not layer-first:

| Package | Purpose |
|---|---|
| `common` | Cross-cutting building blocks: `ApiResponse` envelope, `ApiError` + `GlobalExceptionHandler`, shared constants/enums (`Role`, `AlertStatus`, `WorkType`, `ReadingStatus`), `SecurityUtils`. |
| `config` | Spring configuration: `SecurityConfig`, CORS, JWT properties binding, OpenAPI/Swagger setup. |
| `security` | JWT machinery: `JwtService` (issue/validate), `JwtAuthenticationFilter`, `JwtAuthEntryPoint`, `JwtAccessDeniedHandler`, `CustomUserDetailsService`. |
| `auth` | Public registration/login endpoints and `AuthService`. |
| `user` | User/technician management by the Fab Coordinator (`FabTechnicianController/Service`), the `User` entity and `Role` conversion. |
| `machine` | Machines and the six reference `MachineType`s + their `MachineTypeParameter`s; Fab machine CRUD; shared `/api/machine-types` lookups. |
| `checkup` (+ `checkup.engine`) | Checkup assignment (fab), checkup submission (technician), the unified `Checkup`/`CheckupReading` entities, and the pure `CheckupEngine` scoring core. |
| `alert` | `RiskAlert` entity and its state-machine transitions, split into the Fab-facing (`FabAlertService`) and Manager-facing (`ManagerAlertService`) operations. |
| `repair` | Technician repair completion, the `Repair` entity. |
| `history` | `MachineHistory` — the audit trail written when a repair completes. |
| `work` | `TechnicianWork` — the generic "assignment" entity shared by checkups and repairs (`work_type` discriminates). |
| `dashboard` | Cross-role equipment history dashboard (Manager + Fab Coordinator). |

### Layered request flow

Every feature follows the same layering:

```
Controller  →  Service  →  Repository  →  Entity
 (thin,         (business      (Spring Data     (JPA, Lombok)
  @PreAuthorize,  rules,        JPA)
  DTO mapping)    ownership
                  checks)
```

- **Controllers** are thin: request mapping, `@Valid` DTO binding, `@PreAuthorize` role
  checks, and wrapping the service result in `ApiResponse`.
- **Services** hold all business rules, including a second layer of role/ownership
  verification beyond the URL-level `@PreAuthorize` (re-checking the JWT subject's email
  against the resource owner), per `docs/BEHAVIOR-BASELINE.md §1`.
- **Repositories** are Spring Data JPA interfaces.
- **Entities** are JPA + Lombok, with status/type fields persisted as the exact lowercase
  strings used by the legacy system (`pending`, `sent_to_manager`, `checkup`, `repair`, ...).

### Response envelopes

- **Success** — every 2xx response is wrapped in `common.response.ApiResponse<T>`:
  `{ "success": true, "message": string, "data": <payload|array|null> }`.
- **Error** — every error response is `common.error.ApiError`, produced by a single
  `@RestControllerAdvice` (`GlobalExceptionHandler`):
  `{ "success": false, "message", "status", "path", "timestamp", "errors" }`, with the
  exact exception→status→message mapping specified in `docs/BEHAVIOR-BASELINE.md §5`
  (validation errors → 400 with a field-level `errors` map, access-denied → 403, unhandled
  → 500, etc.).

### Security chain

Stateless JWT authentication, wired in `config.SecurityConfig`:

- `permitAll`: `/api/auth/**`, `/swagger-ui/**`, `/swagger-ui.html`, `/v3/api-docs/**`,
  `/actuator/**`, and `OPTIONS /**`.
- `/api/machine-types/**` → any authenticated role (shared reference data).
- `/api/manager/**` → `ROLE_MANAGER`; `/api/fab/**` → `ROLE_FAB_COORDINATOR`;
  `/api/technician/**` → `ROLE_TECHNICIAN`; `/api/dashboard/**` → `ROLE_MANAGER` or
  `ROLE_FAB_COORDINATOR`.
- Everything else → `authenticated()`.
- `JwtAuthenticationFilter` runs before `UsernamePasswordAuthenticationFilter`, validating
  the bearer token (HS256, Base64-decoded secret) and populating the security context from
  its claims (`sub`=email, `role`, `empId`).
- `JwtAuthEntryPoint` / `JwtAccessDeniedHandler` produce the JSON 401/403 bodies for
  unauthenticated/unauthorized requests, matching the `ApiError` shape.
- `@EnableMethodSecurity` enables `@PreAuthorize` at the controller layer; CSRF is disabled
  (stateless API), sessions are `STATELESS`, passwords are BCrypt-encoded.

### The unified checkup model

The legacy application stored checkup readings in **six separate, duplicate tables** — one
per machine type. The rebuild replaces all of them with a single, type-agnostic schema:

- **`checkups`** — one row per checkup performed (machine, type, check date, final health).
- **`checkup_readings`** — one row per parameter reading on a checkup (parameter number,
  value, status), so any machine type can be represented without schema duplication.
- **`machine_type_parameters`** — reference metadata describing each type's parameters
  (name, unit, good/warning/bad thresholds), used to validate input and to serve the
  parameter-rules endpoint.

### CheckupEngine — the health-scoring core

`checkup.engine.CheckupEngine` is a deliberately framework-free, side-effect-free class (no
Spring types beyond `@Component`, no entities, no DB access) that is a verbatim port of the
legacy scoring logic. It:

1. Classifies each raw parameter value into `good` / `warning` / `bad` using per-type,
   per-parameter threshold rules (six machine types, each with its own boundary table — see
   `docs/BEHAVIOR-BASELINE.md §9` for the exact thresholds, including the intentional gap in
   the Ion Implanter vacuum-pressure rule).
2. Computes `final_health` as a rounded weighted sum (20 pts/parameter for 5-parameter types,
   25 pts/parameter for 4-parameter types; `good`=full weight, `warning`=half, `bad`=zero).
3. Decides whether a `RiskAlert` is warranted (`badCount > 0 OR warningCount > 3`) and, if so,
   its `severity` (`high` if any bad reading, else `medium`).

Because it is pure and framework-free, it is exhaustively unit-tested at every threshold
boundary without needing a Spring context.

## 5. Configuration & profiles

No bare `application.yml` is used (it would merge into the legacy app's default config).
Instead, every profile is explicit and dormant unless activated:

| Profile | File | Purpose |
|---|---|---|
| `local` | `src/main/resources/application-local.yml` | Dev run against the Docker Compose PostgreSQL instance. |
| `itest` | `src/test/resources/application-itest.yml` | Automated tests, H2 running in PostgreSQL compatibility mode. |
| `prod` | `src/main/resources/application-prod.yml` | Production — all values from environment variables, **no defaults for secrets**. |

`spring.jpa.hibernate.ddl-auto=none` in every profile — schema and seed data are applied via
`spring.sql.init` (`mode: always`), reading:

- PostgreSQL: `src/main/resources/db/postgres/schema.sql` + `data.sql`.
- H2 (tests): `src/test/resources/db/h2/schema.sql` + `data.sql`, kept in lockstep.

### Environment variables

All secrets and environment-specific values are supplied via env vars — **never hard-coded
in source**. `local` provides safe fallback defaults for convenience; `prod` does not.

| Variable | Used for | Example (`.env.example`) |
|---|---|---|
| `DB_URL` | JDBC connection URL | `jdbc:postgresql://localhost:5432/ehps` |
| `DB_USERNAME` | Database user | `ehps` |
| `DB_PASSWORD` | Database password | `ehps` |
| `JWT_SECRET` | Base64-encoded HMAC-SHA key for signing/validating JWTs (HS256) | *(see `.env.example`)* |
| `JWT_EXPIRATION_MS` | JWT lifetime in milliseconds | `86400000` (24h) |
| `SERVER_PORT` | HTTP port the app listens on | `8081` |

Copy `.env.example` to `.env` and adjust as needed before running locally.

## 6. Running locally

```bash
# 1. Copy environment defaults
cp .env.example .env

# 2. Start PostgreSQL (Docker Compose)
docker compose up -d

# 3. Run the app on the `local` profile, explicitly targeting the new entry point
#    so the legacy application context is never bootstrapped alongside it.
./mvnw spring-boot:run \
  -Dspring-boot.run.profiles=local \
  -Dspring-boot.run.main-class=com.app.ehps.EhpsApplication
```

The server starts on **`:8081`** (overridable via `SERVER_PORT`). On startup, Spring's SQL
init runs the PostgreSQL schema and seed scripts (`spring.sql.init`, `mode: always`) against
the `public` schema of the `ehps` database defined in `docker-compose.yml`.

## 7. API documentation

- **Swagger UI:** `http://localhost:8081/swagger-ui.html`
- **Live OpenAPI spec:** `http://localhost:8081/v3/api-docs`
- **Static export:** commit a generated copy to `docs/openapi.json` for offline/CI reference
  when needed.

Both are public endpoints (no JWT required to view them).

### Authenticating

```bash
# Register (role: manager | fab_coordinator | technician)
curl -X POST http://localhost:8081/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"name":"...", "email":"...@ehps.com", "phone":"...", "password":"...", "role":"technician", "speciality":"lithography"}'

# Login — returns a JWT in data.token
curl -X POST http://localhost:8081/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"...@ehps.com", "password":"..."}'

# Use the token on every subsequent request
curl http://localhost:8081/api/fab/machines \
  -H 'Authorization: Bearer <token>'
```

### Endpoint groups

The full endpoint table (37 routes) lives in **`docs/API-CONTRACT.md`** — this is a summary
of the groups, all under the `/api` base path:

| Group | Prefix | Who | What |
|---|---|---|---|
| Auth | `/api/auth` | public | Register, login. |
| Machine types | `/api/machine-types` | any authenticated role | Shared reference data: the 6 machine types, their parameter rules, and type-scoped machine/technician lookups. |
| Fab | `/api/fab/**` | `ROLE_FAB_COORDINATOR` | Own machines, own technicians, checkup assignment, alert pending/escalation/approved-unassigned, candidate technicians, repair assignment. |
| Manager | `/api/manager/**` | `ROLE_MANAGER` | All alerts, pending-approval alerts, approve/reject, all machines/technicians (read). |
| Technician | `/api/technician/**` | `ROLE_TECHNICIAN` | Own account (password change), assigned checkups + submitting results, assigned repair alerts + completing repairs. |
| Dashboard | `/api/dashboard/equipment` | `ROLE_MANAGER` or `ROLE_FAB_COORDINATOR` | Equipment history, filterable by date range and machine type. |

## 8. Testing

```bash
./mvnw test
```

- **Unit tests** (JUnit 5 + Mockito, `@ExtendWith(MockitoExtension.class)`) — no Spring
  context; services are tested against mocked repositories, and the `CheckupEngine` is
  tested as a plain Java class.
- **Integration tests** — `@SpringBootTest(classes = com.app.ehps.EhpsApplication.class)`
  with `@ActiveProfiles("itest")` and `@AutoConfigureMockMvc`, driving real HTTP requests
  through the full security chain against H2 running in PostgreSQL compatibility mode.
  `classes=` is always explicit so the legacy application context can never be picked up
  by mistake.
- The `CheckupEngine` in particular has **exhaustive boundary tests** — every `good`/
  `warning`/`bad` threshold, for every parameter, for every one of the six machine types
  (including the intentional gap in the Ion Implanter's vacuum-pressure rule), is pinned
  with its own test case so the scoring behavior can never silently drift from
  `docs/BEHAVIOR-BASELINE.md §9`.

All new tests live under `com.app.ehps` in `src/test/java`; legacy tests under
`com.app.ehps_api` are untouched.

## 9. Project background

This backend is a ground-up rebuild of a legacy EHPS Spring Boot application, developed
using the **strangler-fig** pattern: the new app (`com.app.ehps`) is built alongside the
untouched legacy app (`com.app.ehps_api`) in the same repository, and will be cut over once
complete. The legacy app remains the behavioral oracle throughout.

- All business semantics — validation rules, thresholds, state machines, authorization
  checks, error messages — are preserved **exactly** as documented in
  [`docs/BEHAVIOR-BASELINE.md`](docs/BEHAVIOR-BASELINE.md).
- The HTTP surface has been deliberately corrected (proper status codes, noun-based
  sub-resource actions instead of verbs, consolidated machine-type endpoints, a consistent
  success envelope) as specified in [`docs/API-CONTRACT.md`](docs/API-CONTRACT.md).
