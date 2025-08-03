# Test Plan — GET /api/v1/consent/status/{verificationId}

This document lists **all tests** for the *verification status* endpoint and its service/repository/DTO layers.

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
