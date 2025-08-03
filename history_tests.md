# /history Endpoint – Comprehensive Test Plan

This document lists **all necessary tests** for the `/api/v1/consent/history/{userId}` endpoint and its service/repository layers, based on the current implementation. No code is included—only what each test should verify.

---

## 3) Repository Tests (`ConsentLedgerRepository`)

> Use @DataJpaTest with real DB (H2/MySQL) and entity mappings.

1. **`findByUserId(Pageable)` honors composite sort** ✅ IMPLEMENTED
   - With data where multiple rows share `consentTimestamp` but differ in `createdAt`
   - Verify order: `consentTimestamp DESC`, then `createdAt DESC`.

2. **`findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc`** ✅ IMPLEMENTED
   - Returns the most recent entry per composite ordering.

3. **`findFirstByUserIdAndConsentTypeAndConsentStatusOrderByConsentTimestampDescCreatedAtDesc`** ✅ IMPLEMENTED
   - Filters by status and returns correct latest entry.

4. **`findExpiredConsents(now)`** ✅ IMPLEMENTED
   - Returns only rows with `retentionExpiresAt <= now`.

5. **`findActiveGrantByUserTypeAndVersion`** ✅ IMPLEMENTED
   - Returns the GRANTED row matching user/type/version; returns empty when not found or last action is WITHDRAWN.

6. **`countActiveGrantsByUserAndType`** ✅ IMPLEMENTED
   - Counts only rows with `consentStatus = GRANTED` for the type/user.

7. **`existsWithdrawalByUserTypeAndVersion`** ✅ IMPLEMENTED
   - True when a WITHDRAWN row exists for user/type/version; false otherwise.

8. **`findByJurisdictionAndRegion`** ✅ IMPLEMENTED
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
