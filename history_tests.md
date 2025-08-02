# /history Endpoint – Comprehensive Test Plan

This document lists **all necessary tests** for the `/api/v1/consent/history/{userId}` endpoint and its service/repository layers, based on the current implementation. No code is included—only what each test should verify.

---

## 2) Service Unit Tests (`ConsentServiceImpl#getConsentHistory(userId, page, size)`)

> Mock repositories; verify logic, mapping, error handling.

1. **Validates page and size bounds** ✅ IMPLEMENTED
   - `page < 0` ⇒ throws `ResponseStatusException(400)`
   - `size <= 0` or `size > 100` ⇒ throws `ResponseStatusException(400)`.

2. **Invalid `userId` UUID** ✅ IMPLEMENTED
   - Non‑UUID string ⇒ `ResponseStatusException(400)`.

3. **Empty page result handling** ✅ IMPLEMENTED
   - Repository returns empty `Page` ⇒ service returns `entries=[]`, `total=0`, correct metadata via wrapper.

4. **Batch coverage fetch (no N+1)** ✅ IMPLEMENTED
   - With N ledger rows, service calls `findByConsentIds` **once** with all relevant IDs; never calls per‑row coverage fetch methods.

5. **Mapping: all fields copied correctly** ❌ NOT IMPLEMENTED
   - Verify each `ConsentHistoryEntry` field mirrors `ConsentLedger` values; `parentVerificationId` and `withdrawnConsentId` stringified or null; timestamps preserved.

6. **`coveredKids` distinct + sorted** ❌ NOT IMPLEMENTED
   - Input coverage includes duplicates/unordered ⇒ output is unique and sorted.

7. **Ordering not overridden in service** ❌ NOT IMPLEMENTED
   - Service honors repository/page ordering (no resorting).

8. **Repository exception surfaces as 500** ❌ NOT IMPLEMENTED
   - If `consentLedgerRepository.findByUserId(..)` throws, service wraps with `ResponseStatusException(500)`; similarly for `consentChildCoverageRepository.findByConsentIds(..)`.

9. **Pagination metadata computation (wrapper)** ❌ NOT IMPLEMENTED
   - Using total elements from `Page`, `PaginatedConsentHistoryResponse.from` produces correct `totalPages`, `hasNext`, `hasPrevious` for:
     - total=0, size=20
     - total=1, size=20
     - total=20, size=20
     - total=21, size=20 (2 pages), testing pages 0 and 1.

10. **Coverage absent for some consents** ❌ NOT IMPLEMENTED
    - Some consent IDs missing in `coverageMap` ⇒ their `coveredKids=[]`.

---

## 3) Repository Tests (`ConsentLedgerRepository`)

> Use @DataJpaTest with real DB (H2/MySQL) and entity mappings.

1. **`findByUserId(Pageable)` honors composite sort** ❌ NOT IMPLEMENTED
   - With data where multiple rows share `consentTimestamp` but differ in `createdAt`
   - Verify order: `consentTimestamp DESC`, then `createdAt DESC`.

2. **`findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc`** ✅ IMPLEMENTED
   - Returns the most recent entry per composite ordering.

3. **`findFirstByUserIdAndConsentTypeAndConsentStatusOrderByConsentTimestampDescCreatedAtDesc`** ❌ NOT IMPLEMENTED
   - Filters by status and returns correct latest entry.

4. **`findExpiredConsents(now)`** ❌ NOT IMPLEMENTED
   - Returns only rows with `retentionExpiresAt <= now`.

5. **`findActiveGrantByUserTypeAndVersion`** ✅ IMPLEMENTED
   - Returns the GRANTED row matching user/type/version; returns empty when not found or last action is WITHDRAWN.

6. **`countActiveGrantsByUserAndType`** ❌ NOT IMPLEMENTED
   - Counts only rows with `consentStatus = GRANTED` for the type/user.

7. **`existsWithdrawalByUserTypeAndVersion`** ✅ IMPLEMENTED
   - True when a WITHDRAWN row exists for user/type/version; false otherwise.

8. **`findByJurisdictionAndRegion`** ❌ NOT IMPLEMENTED
   - Filters by exact jurisdiction and region (case preserved per stored data).

9. **`findByConsentTimestampBetween(from, to)`** ❌ NOT IMPLEMENTED
   - Includes boundaries and excludes out‑of‑range rows.

10. **`findByParentVerificationId`** ❌ NOT IMPLEMENTED
    - Returns rows matching the verification id.

11. **Entity persistence defaults (`@PrePersist`)** ❌ NOT IMPLEMENTED
    - `createdAt` auto‑populates in UTC when null on insert; verify precision/zone expectations.

---

## 4) DTO & Serialization Tests

1. **`PaginatedConsentHistoryResponse.from` metadata math** ❌ NOT IMPLEMENTED
   - Validate `totalPages`, `hasNext`, `hasPrevious` for combinations:
     - total=0,size=20 ⇒ totalPages=0, hasNext=false, hasPrevious=false
     - total=1,size=20 ⇒ totalPages=1, hasNext=false (page=0), hasPrevious=false
     - total=20,size=20 ⇒ totalPages=1
     - total=21,size=20 ⇒ totalPages=2 (page=0: hasNext=true; page=1: hasPrevious=true).

2. **JSON serialization shape** ❌ NOT IMPLEMENTED
   - Serialize `PaginatedConsentHistoryResponse` with one entry using Jackson `ObjectMapper`
   - Ensure property names and enum string values match API contract; nulls included where expected (e.g., `parentVerificationId`, `withdrawnConsentId`).

3. **Deterministic `coveredKids` representation** ❌ NOT IMPLEMENTED
   - Given unsorted input, serialized JSON shows sorted order.

---

## 5) Performance / N+1 Guard (Integration or Slice)

1. **Coverage retrieved in a single query** ❌ NOT IMPLEMENTED
   - Instrument queries (e.g., datasource proxy) for a request with N entries; assert coverage query executes once, not N times.

2. **Reasonable latency for large page** ❌ NOT IMPLEMENTED
   - With `size=100` and typical data volumes, endpoint responds within acceptable SLA (define threshold for your project).

---

## 6) Edge‑Case & Ordering Scenarios

1. **Multiple events same `consentTimestamp` across pages** ❌ NOT IMPLEMENTED
   - Create > `size` entries with identical `consentTimestamp` but different `createdAt`
   - Assert stable ordering across page boundaries.

2. **WITHDRAWN followed by GRANTED (same type)** ❌ NOT IMPLEMENTED
   - Ensure both appear in history in correct chronological order per composite sort.

3. **Null optional fields** ❌ NOT IMPLEMENTED
   - Rows without `parentVerificationId` or `locale/region` serialize with nulls; service doesn't throw.

4. **Coverage rows with extraneous/duplicate kid IDs** ❌ NOT IMPLEMENTED
   - Duplicates removed and sorted per service logic.

---

## 7) Negative / Error Propagation (Service & Controller)

1. **Repository throws unexpected runtime exception (ledger)** ❌ NOT IMPLEMENTED
   - Service returns `ResponseStatusException(500)`; controller returns `500` with error payload.

2. **Coverage repository throws exception** ❌ NOT IMPLEMENTED
   - Service returns `ResponseStatusException(500)`; controller returns `500`.

---

## 8) Contract / Backward‑Compatibility Checks

1. **Enum serialization is textual** ❌ NOT IMPLEMENTED
   - Confirm enums (`ConsentType`, `ConsentStatus`, `LawfulBasis`, `ConsentSource`) are serialized as strings (no ordinal leakage).

2. **Timestamp format** ❌ NOT IMPLEMENTED
   - Confirm `LocalDateTime` values serialize in the expected ISO format per project's Jackson config.

---

### Notes
- Use realistic seed data: mix of consent types, versions, GRANTED/WITHDRAWN records, varied timestamps and createdAt values, and coverages (including duplicates).
- For ordering tests, choose timestamps/createdAt with deliberate collisions to exercise tie‑break logic.
- Prefer @DataJpaTest for repository tests; MockMvc + Spring Security for integration; Mockito + JUnit for service unit tests.
