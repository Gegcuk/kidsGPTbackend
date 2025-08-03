# /history Endpoint – Comprehensive Test Plan

This document lists **all necessary tests** for the `/api/v1/consent/history/{userId}` endpoint and its service/repository layers, based on the current implementation. No code is included—only what each test should verify.

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
