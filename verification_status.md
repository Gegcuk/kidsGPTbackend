# Test Plan — GET /api/v1/consent/status/{verificationId}

This document lists **all tests** for the *verification status* endpoint and its service/repository/DTO layers.

---

## 1) Controller / Integration tests (`ConsentController#getConsentStatus`)

> Scope: request/response wiring, exception mapping via `GlobalExceptionHandler`, JSON shape, HTTP codes.  
> Setup: `@WebMvcTest(ConsentController.class)` + `@AutoConfigureMockMvc(addFilters = false)`; `@MockBean ConsentService`.
> Accept header: prefer `application/json` for 200; `application/problem+json` for error cases.

### 1.1 Invalid UUID → 400
- **Given** path `verificationId = "not-a-uuid"`
- **When** GET `/api/v1/consent/status/not-a-uuid`
- **Then** `400 Bad Request` with body:
  - `error` = "Invalid verification ID format"
  - `status` = 400
  - `details[0]` = "Invalid verification ID format"
- **And** service not called.

### 1.2 Verification not found → 404
- **Given** service throws `ResponseStatusException(404, "Verification not found")`
- **When** GET `/status/{uuid}`
- **Then** `404 Not Found` and body contains `"Verification not found"`.

### 1.3 Verification expired → 410
- **Given** service throws `ResponseStatusException(410, "Verification has expired")`
- **Then** response is `410 Gone` with body containing `"Verification has expired"`.

### 1.4 Verification not completed → 409
- **Given** service throws `ResponseStatusException(409, "Verification not completed")`
- **Then** response is `409 Conflict` with body containing `"Verification not completed"`.

### 1.5 Happy path — empty status (no consents) → reconsentNeeded=true
- **Given** service returns
  ```json
  {
    "latestByType": [],
    "reconsentNeeded": true,
    "consentId": null
  }
  ```
- **Then** `200 OK` and JSON matches above (array present but empty).

### 1.6 Happy path — populated status (all current) → reconsentNeeded=false
- **Given** service returns 1–N `latestByType` entries where each `status` ≠ `WITHDRAWN`
  and policy versions match the latest.
- **Then** `200 OK`, `reconsentNeeded=false`, enums serialized as strings.

### 1.7 Includes most recent consentId
- **Given** service returns some UUID in `consentId`
- **Then** JSON field `consentId` equals expected.

### 1.8 Content types
- **200** → `application/json`
- **errors** → `application/problem+json`

### 1.9 Controller does not swallow exceptions
- **Given** service throws unchecked exception
- **Then** `500 Internal Server Error` with default `GlobalExceptionHandler` payload
  (`error="Internal Server Error"`).

---

## 2) Service Unit tests (`ConsentServiceImpl#getConsentStatus`)

> Scope: business rules, branching, time handling, repository interactions.  
> Setup: `@ExtendWith(MockitoExtension.class)` with mocks for repositories; inject a **fixed `Clock`** via `ReflectionTestUtils.setField(service, "clock", Clock.fixed(...))`.

### 2.1 Invalid verificationId format → 400
- **Given** `verificationId="bad"`
- **Then** throw `ResponseStatusException(400, "Invalid verification ID format")`.
- **And** no repository calls.

### 2.2 Verification not found → 404
- **Given** `parentVerificationRepository.findById(id)` returns empty
- **Then** throw `ResponseStatusException(404, "Verification not found")`.

### 2.3 Verification expired (uses Clock) → 410
- **Given** `verification.expiresAt` < `Instant.now(clock)`
- **Then** throw `ResponseStatusException(410, "Verification has expired")`.

### 2.4 Verification not completed → 409
- **Given** `verification.status` ∈ {`PENDING`, `FAILED`} (anything != `VERIFIED`)
- **Then** throw `ResponseStatusException(409, "Verification not completed")`.

### 2.5 Happy path — no consents ⇒ reconsentNeeded=true
- **Given** `buildEffectiveConsentStatus` would return `[]` (mock via repo methods)
- **Then** service returns `latestByType=[]`, `reconsentNeeded=true`, `consentId=null`.
- **Verify** no lookup of policies is required to decide truthy.

### 2.6 Happy path — WITHDRAWN in any type ⇒ reconsentNeeded=true
- **Given** latest entry for one type has `status=WITHDRAWN`
- **Then** `reconsentNeeded=true` regardless of policy versions.

### 2.7 Version is null ⇒ reconsentNeeded=true
- **Given** one entry has `version=null`
- **And** there exists an active policy for that type (any version)
- **Then** `true` (treated as outdated).

### 2.8 Latest policy by locale — URL has `en-GB`
- **Given** `policyUrl` includes a path segment `en-GB`
- **And** `consentPoliciesRepository.findActivePoliciesByTypeLocaleAndDate(type, "en-GB", today)` returns item with `version="1.2.3"`
- **When** entry `version="1.2.3"`
- **Then** no reconsent for that type.

### 2.9 Latest policy without locale (no locale derivable)
- **Given** `policyUrl=null` or has no locale segment
- **And** `findActivePoliciesByTypeAndDate(type, today)` returns `"2.0.0"`
- **When** entry `version="1.9.9"`
- **Then** `reconsentNeeded=true`.

### 2.10 No active policy available
- **Given** repo returns empty for the type/locale/date
- **Then** treat as **not outdated** (do not force reconsent for that type).

### 2.11 Most recent consentId selection (ordering)
- **Given** repo `findByUserIdOrderByConsentTimestampDescCreatedAtDesc(parentId, PageRequest.of(0,1))` returns a single row
- **Then** returned `consentId` equals that row’s id.
- **Also** with **two** rows having same `consentTimestamp` but different `createdAt`, verify the repo’s ordering yields the later `createdAt` first → id chosen accordingly.

### 2.12 Repository interactions
- **Verify** `parentVerificationRepository.findById()` exactly once
- **Verify** either locale-aware or non-locale policy lookup is used per entry under test
- **Verify** `consentLedgerRepository.findByUserIdOrderByConsentTimestampDescCreatedAtDesc(parentId, PageRequest.of(0,1))` called once.

### 2.13 Logging (optional)
- **Given** a `ListAppender` on the service logger
- **Then** key log lines (expired, conflict, counts) appear once per path.

### 2.14 Null-safety & defaults
- **Given** `policyUrl` malformed or unexpected
- **Then** `deriveLocaleFromPolicyUrl` returns null and code falls back to non-locale lookup without exception.

---

## 3) Repository tests (recommended)

> Scope: verify JPQL/derived-method semantics, ordering, and date filtering.  
> Setup: `@DataJpaTest` with Testcontainers or embedded DB; use UTC timestamps.

### 3.1 `ParentVerificationRepository#findById`
- Basic happy-path for persisting and fetching by ID.

### 3.2 `ConsentLedgerRepository#findByUserIdOrderByConsentTimestampDescCreatedAtDesc`
- **Given** multiple rows with same `consentTimestamp` but different `createdAt`
- **Then** result order is desc by `consentTimestamp`, then desc by `createdAt` (deterministic).

### 3.3 `ConsentPoliciesRepository#findActivePoliciesByTypeAndDate`
- **Given** policies with `effectiveDate` before/after `today` and `isActive=true/false`
- **Then** only active with `effectiveDate <= today` returned, ordered by `effectiveDate DESC`.

### 3.4 `ConsentPoliciesRepository#findActivePoliciesByTypeLocaleAndDate`
- Same as 3.3 but constrained by `locale`.

---

## 4) DTO / Serialization (contract tests)

> Scope: JSON mapping stability for `ConsentStatusResponse` and nested records.

### 4.1 Enums serialize as strings
- **Given** `ConsentStatusByType.type/status` populated
- **Then** JSON shows `"DATA_PROCESSING"`, `"GRANTED"` etc.

### 4.2 Nulls and empty collections
- `consentId` may be `null`
- `latestByType` must be present as an **array** (possibly empty).

### 4.3 Timestamp format
- `timestamp` serialized as ISO-8601 without timezone suffix (per `LocalDateTime`).

### 4.4 Example snapshot (optional)
- Snapshot of a typical 200 response to detect accidental field/format changes.

---

## 5) Negative / robustness (optional)

- Random/garbage `policyUrl` is ignored safely (no exception).
- Very large `latestByType` list still processed (performance sanity).
- Service tolerates unknown consent types in data (if any appear).

---

## Notes & Fixtures

- Use a fixed clock: `Clock.fixed(Instant.parse("2025-01-15T12:00:00Z"), ZoneOffset.UTC)`.
- Build `ParentVerification` with `VERIFIED`, a future `expiresAt`, and relevant method/parentId.
- For policy tests, insert multiple policies to exercise ordering & locale selection.
- Prefer UUID constants for determinism.
- Keep database timezone UTC; set Hibernate/JDBC to UTC for repeatability.
