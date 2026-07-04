# EHPS — Target REST API Contract (frozen)

The rebuild modernizes the HTTP surface (the Angular FE will be adapted). **Business behavior is
unchanged** — see `BEHAVIOR-BASELINE.md`. Every coder builds against this contract; do not deviate
without an orchestrator decision.

## Global conventions

- **Base path:** `/api`. Role-scoped groups keep their prefixes for clean authorization boundaries:
  `/api/fab/**` (FAB_COORDINATOR), `/api/manager/**` (MANAGER), `/api/technician/**` (TECHNICIAN).
  Truly shared reference data lives at `/api/machine-types` (any authenticated role).
- **Success envelope** (all 2xx): `{ "success": true, "message": string, "data": <payload|array|null> }`.
- **Error envelope** (unchanged from legacy `ApiErrorResponse`): `{ success:false, message, status, path, timestamp, errors }`.
- **Status codes:** create → **201**; read/update/action → **200**; validation/business error → 400/401/403/404/409 per baseline.
- **Auth:** `Authorization: Bearer <jwt>` on every non-public route. Public: `/api/auth/**`, swagger, `/v3/api-docs/**`, `/actuator/**`.
- Path variables unchanged in meaning: `{machineId}`, `{technicianId}`, `{typeId}`, `{alertId}`.

## Key corrections applied
1. **Verb-in-path → noun sub-resource actions** (`/approve`→`/approval`, `/send-to-manager`→`/escalation`, etc.).
2. **Proper status codes** (register + all creates → 201).
3. **Consolidated machine-types**: 3 legacy endpoints → one shared `GET /api/machine-types`; parameter rules → `GET /api/machine-types/{typeId}/parameters`.
4. **Consistent success envelope** wrapping the former ad-hoc response DTOs (their `message` moves to `envelope.message`, entity to `envelope.data`).

---

## Endpoint mapping (37 legacy → target)

### Auth — `/api/auth` (public)
| Legacy | Target | Status | Notes |
|---|---|---|---|
| POST /api/auth/register | **POST /api/auth/register** | **201** | body `RegisterRequest`; data=`UserResponse`, no token |
| POST /api/auth/login | **POST /api/auth/login** | 200 | data=`{ token, user }` |

### Machine types — `/api/machine-types` (any authenticated role) — reference data
| Legacy | Target | Status |
|---|---|---|
| GET /api/fab/machines/types · GET /api/fab/checkups/types · GET /api/manager/machine-types | **GET /api/machine-types** | 200 |
| GET /api/technician/checkups/types/{typeId}/parameters | **GET /api/machine-types/{typeId}/parameters** | 200 |
| GET /api/fab/checkups/types/{typeId}/machines | **GET /api/machine-types/{typeId}/machines** | 200 (FAB+MANAGER) |
| GET /api/fab/checkups/types/{typeId}/technicians | **GET /api/machine-types/{typeId}/technicians** | 200 (FAB) |

### Fab — machines `/api/fab/machines` (FAB_COORDINATOR, own-scoped)
| Legacy | Target | Status |
|---|---|---|
| POST /api/fab/machines | **POST /api/fab/machines** | **201** |
| GET /api/fab/machines | GET /api/fab/machines | 200 |
| GET /api/fab/machines/{machineId} | GET /api/fab/machines/{machineId} | 200 |
| PUT /api/fab/machines/{machineId} | PUT /api/fab/machines/{machineId} | 200 |

### Fab — technicians `/api/fab/technicians` (FAB_COORDINATOR)
| Legacy | Target | Status |
|---|---|---|
| POST /api/fab/technicians | **POST /api/fab/technicians** | **201** |
| GET /api/fab/technicians | GET /api/fab/technicians | 200 |
| GET /api/fab/technicians/{technicianId} | GET /api/fab/technicians/{technicianId} | 200 |
| PUT /api/fab/technicians/{technicianId} | PUT /api/fab/technicians/{technicianId} | 200 |

### Fab — checkup assignment (FAB_COORDINATOR)
| Legacy | Target | Status |
|---|---|---|
| POST /api/fab/checkups/assign | **POST /api/fab/checkup-assignments** | **201** |

### Fab — alerts `/api/fab/alerts` (FAB_COORDINATOR, own-scoped)
| Legacy | Target | Status |
|---|---|---|
| GET /api/fab/alerts/pending | GET /api/fab/alerts/pending | 200 |
| GET /api/fab/alerts/approved-unassigned | GET /api/fab/alerts/approved-unassigned | 200 |
| GET /api/fab/alerts/{alertId}/technicians | **GET /api/fab/alerts/{alertId}/candidate-technicians** | 200 |
| POST /api/fab/alerts/{alertId}/send-to-manager | **POST /api/fab/alerts/{alertId}/escalation** | 200 |
| POST /api/fab/alerts/{alertId}/assign-repair | **POST /api/fab/alerts/{alertId}/repair-assignment** | **201** |

### Manager — alerts `/api/manager/alerts` (MANAGER)
| Legacy | Target | Status |
|---|---|---|
| GET /api/manager/alerts | GET /api/manager/alerts | 200 |
| GET /api/manager/alerts/pending-approval | GET /api/manager/alerts/pending-approval | 200 |
| POST /api/manager/alerts/{alertId}/approve | **POST /api/manager/alerts/{alertId}/approval** | 200 |
| POST /api/manager/alerts/{alertId}/reject | **POST /api/manager/alerts/{alertId}/rejection** | 200 |

### Manager — machines & technicians `/api/manager` (MANAGER, all-scoped)
| Legacy | Target | Status |
|---|---|---|
| GET /api/manager/machines | GET /api/manager/machines | 200 |
| GET /api/manager/technicians | GET /api/manager/technicians | 200 |
| GET /api/manager/machine-types | → GET /api/machine-types (shared) | 200 |

### Technician — account `/api/technician/account` (TECHNICIAN)
| Legacy | Target | Status |
|---|---|---|
| POST /api/technician/account/change-password | **PUT /api/technician/account/password** | 200 |

### Technician — checkups `/api/technician` (TECHNICIAN, assigned-scoped)
| Legacy | Target | Status |
|---|---|---|
| GET /api/technician/checkups/assigned | **GET /api/technician/checkup-assignments** | 200 |
| GET /api/technician/checkups/machines/{machineId} | GET /api/technician/checkups/machines/{machineId} | 200 |
| POST /api/technician/checkups/machines/{machineId}/perform | **POST /api/technician/checkups/machines/{machineId}/results** | **201** |

### Technician — repairs `/api/technician/repairs` (TECHNICIAN, assigned-scoped)
| Legacy | Target | Status |
|---|---|---|
| GET /api/technician/repairs/alerts | GET /api/technician/repairs/alerts | 200 |
| GET /api/technician/repairs/alerts/{alertId} | GET /api/technician/repairs/alerts/{alertId} | 200 |
| GET /api/technician/repairs/machines/{machineId} | GET /api/technician/repairs/machines/{machineId} | 200 |
| POST /api/technician/repairs/alerts/{alertId}/complete | **POST /api/technician/repairs/alerts/{alertId}/completion** | **201** |

### Equipment dashboard (MANAGER + FAB_COORDINATOR)
| Legacy | Target | Status |
|---|---|---|
| GET /api/dashboard/equipment?fromDate&toDate&typeId | GET /api/dashboard/equipment?fromDate&toDate&typeId | 200 |

---

## SecurityConfig authorization (target)
- `permitAll`: `/api/auth/**`, `/swagger-ui/**`, `/swagger-ui.html`, `/v3/api-docs/**`, `/actuator/**`, `OPTIONS /**`.
- `GET /api/machine-types/**` → any authenticated (`authenticated()`), plus method-level scoping where a
  sub-list is role-specific (`/technicians` FAB only, `/machines` FAB+MANAGER).
- `/api/manager/**` → `hasRole('MANAGER')`; `/api/fab/**` → `hasRole('FAB_COORDINATOR')`;
  `/api/technician/**` → `hasRole('TECHNICIAN')`; `/api/dashboard/**` → `hasAnyRole('MANAGER','FAB_COORDINATOR')`.
- Everything else → `authenticated()`. `@EnableMethodSecurity` for `@PreAuthorize` on controllers.
