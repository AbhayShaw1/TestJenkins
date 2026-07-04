# EHPS Rebuild — Conventions (read before writing any code)

The rebuild uses the **strangler-fig** pattern: the new app is built **alongside** the untouched legacy
app and cut over at the end. Legacy stays green as the behavioral oracle the whole way.

## Hard rules
1. **NO new dependencies.** Only these are allowed (already in `pom.xml`): spring-boot-starter-web,
   -data-jpa, -security, -validation, -actuator, -test; h2; postgresql; springdoc-openapi-starter-webmvc-ui;
   jjwt-api/impl/jackson (0.12.7); slf4j-api; lombok; junit-jupiter; mockito-core; mockito-junit-jupiter;
   spring-security-test. Do not add anything to `pom.xml`.
2. **Additive only.** Do NOT modify or delete ANY existing file. Off-limits: everything under
   `com.app.ehps_api` (Java + tests), `src/main/resources/application.properties`,
   `src/test/resources/application-test.properties`, `src/test/resources/schema.sql`+`data.sql`, `pom.xml`,
   `Jenkinsfile`. You may only ADD new files.
3. **New base package: `com.app.ehps`** (note: no `_api`). New Spring Boot entrypoint:
   `com.app.ehps.EhpsApplication` (`@SpringBootApplication`). Component scanning is rooted here, so it
   never loads legacy beans and vice-versa — the two apps are runtime-isolated.
4. **Feature-first packages** under `com.app.ehps`: `common`, `config`, `security`, `auth`, `user`,
   `machine`, `checkup`, `alert`, `repair`, `dashboard`. Keep classes small and single-purpose.

## Profiles & config (avoid collision with legacy default config)
- Do **NOT** add a bare `application.yml` (it would merge into the legacy app's config). All new config
  lives in **profile-specific** files that are dormant unless their profile is active:
  - `application-local.yml` (src/main/resources) — dev run against Docker Postgres; all secrets via env
    vars with safe local defaults (`${DB_URL:jdbc:postgresql://localhost:5432/ehps}` etc.).
  - `application-prod.yml` (src/main/resources) — env vars, no defaults for secrets.
  - `application-itest.yml` (src/test/resources) — H2 in PostgreSQL mode for automated tests.
- The new app is always run/tested with an explicit profile:
  - Dev run: `./mvnw spring-boot:run -Dspring-boot.run.profiles=local -Dspring-boot.run.main-class=com.app.ehps.EhpsApplication`
  - Tests: annotate with `@ActiveProfiles("itest")`.
- `spring.jpa.hibernate.ddl-auto=none` everywhere. Schema/seed applied via `spring.sql.init`:
  - Postgres DDL: `src/main/resources/db/postgres/schema.sql` + `data.sql`.
  - H2 DDL (tests): `src/test/resources/db/h2/schema.sql` + `data.sql`, kept in lockstep with Postgres.
  - Point each profile's `spring.sql.init.schema-locations`/`data-locations` at the right pair; `mode=always`.

## API conventions (see `docs/API-CONTRACT.md`)
- Success envelope for every 2xx: `ApiResponse<T> { boolean success=true; String message; T data; }`.
- Errors: reuse the legacy error shape via a new `common.error.ApiError`
  `{ success:false, message, status, path, timestamp, errors }` + a `@RestControllerAdvice`
  `GlobalExceptionHandler` with the exact same exception→status mapping as `BEHAVIOR-BASELINE.md §5`.
- Creates → 201, reads/updates/actions → 200. Business errors via `ResponseStatusException(status, reason)`.
- Controllers thin; business rules in services (see `BEHAVIOR-BASELINE.md`). Use `@PreAuthorize` per contract.

## Domain conventions
- Entities are JPA + Lombok (`@Getter @Setter @NoArgsConstructor @AllArgsConstructor`), matching the new
  schema in `db/*/schema.sql`. Use constants/enums from `common` (`Role`, `AlertStatus`, `WorkType`,
  `MachineTypeCode`) rather than magic strings. Status values persisted as lowercase strings exactly as
  in `BEHAVIOR-BASELINE.md`.
- Logged-in user resolved via `common.security.SecurityUtils.currentUserEmail()` (no scattered
  `SecurityContextHolder` calls).

## Testing conventions
- New tests live under `com.app.ehps` in `src/test/java`. Do not touch legacy tests.
- Unit tests: JUnit 5 + Mockito (`@ExtendWith(MockitoExtension.class)`), no Spring context.
- Integration/slice tests: `@SpringBootTest(classes = com.app.ehps.EhpsApplication.class)` +
  `@ActiveProfiles("itest")`, MockMvc (`@AutoConfigureMockMvc`) driving real HTTP through the security
  chain against H2. Be explicit with `classes=` so the two apps never get confused.
- Assertions: prefer AssertJ (`org.assertj`, bundled with spring-boot-starter-test).
