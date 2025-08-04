# verify_initiate_tests.md

**Subject:** `/api/v1/verification/initiate` (Initiate parent verification)  
**Layers covered:** DTO validation, Controller (integration), Service (unit), Repository (unit/integration), Concurrency, Resilience, Security/Abuse, Observability, Contract.

---

## 0) Preconditions & Assumptions
- Verification methods supported: `EMAIL` (implemented), `SMS` (stub).  
- TTL configured via `verification.ttl-minutes` (default 30).  
- Hashing uses HMAC-SHA256 with configurable pepper.  
- Idempotency: *reuse* existing **PENDING, unexpired** record for same `(parentId, method, contactHash)`; rotate code & extend expiry.  
- Email dispatch occurs **after transaction commit**; failures must **not** affect DB outcome.  
- Entity stores `contact_info_hash`, `verification_code_hash` as `VARBINARY(32)` (or 64 — ensure schema match per environment).

---

## 1) DTO & Validation Tests

### 1.1 `VerificationInitiateRequest` happy cases
- **EMAIL valid** → passes: lowercase/uppercase address accepted; normalization to lowercase before hashing.
- **SMS valid (E.164)** → passes (even if SMS dispatch not implemented).

### 1.2 `VerificationInitiateRequest` invalid combinations
- **Missing parentId** → `400` with field error.
- **Missing verificationMethod** → `400` with field error.
- **Missing contactInfo** → `400` with field error.
- **EMAIL with invalid address** → `400` (custom message).
- **SMS with non‑E.164** → `400` (custom message).
- **Unsupported method value** → `400` with message “Unsupported verification method”.

### 1.3 Normalization properties
- **EMAIL case-insensitivity**: `John.Doe@Example.com` and `john.doe@example.com` produce identical contact hash.
- **Whitespace trimming**: leading/trailing spaces are ignored before hashing.
- **Phone already E.164**: trimmed string hashed identically.

---

## 2) Controller Integration Tests (`@WebMvcTest` or `@SpringBootTest(webEnvironment=RANDOM_PORT)`)

### 2.1 Success responses
- **201 Created** for *new* verification; `Location` points to `/status/{id}`; headers: `Cache-Control: no-store`, `Pragma: no-cache`, `Expires: 0`, `X-Verification-Id` present; body contains expected fields.
- **200 OK** for *reuse* of existing pending verification (idempotent). Same headers as above.

### 2.2 Validation failures → 400
- Invalid/missing fields from §1.2 map to `ErrorResponse` with messages.

### 2.3 Parent not found → 404
- Nonexistent `parentId` returns `404` (service throws `ResponseStatusException` NOT_FOUND).

### 2.4 Unsupported/Not implemented method behavior
- **SMS** request returns **200/201** but logs a warning (no email sent). Ensure no server error is produced.

### 2.5 No PII leakage in logs (if using test appender)
- Verify that application logs mask email domain (e.g., `***@example.com`) and never log full address.

---

## 3) Service Unit Tests (`ParentVerificationServiceImpl`)

### 3.1 Happy path – new verification (EMAIL)
- **Given** parent exists, no pending existing  
- **When** initiate  
- **Then**: entity saved with `PENDING`, correct `expiresAt = now + TTL`, new `verificationId`, `attemptCount=0`; email dispatch scheduled `afterCommit`; response has `201 semantics` (via controller).

### 3.2 Idempotent reuse (EMAIL)
- **Given** pending record exists for same `(parentId, EMAIL, contactHash)` and not expired  
- **When** initiate  
- **Then**: record reused, **code rotated**, `expiresAt` extended, `newlyCreated=false`.

### 3.3 No pending for same method but other pending exists
- **Given** pending exists for different contact/method  
- **When** initiate  
- **Then**: **new** record created for the current contact/method.

### 3.4 Race condition: unique constraint / save conflict
- **Given** concurrent creation leads to `DataIntegrityViolationException`  
- **When** service catches and finds existing pending via repository method  
- **Then**: reuse that record, rotate code, extend expiry, return success.

### 3.5 Email scheduling
- **Given** email method and generated code  
- **When** initiate  
- **Then**: `TransactionSynchronization.afterCommit` enqueues `emailService.sendVerificationEmail(normalizedEmail, code)`.

### 3.6 Email disabled
- **Given** `emailConfig.enabled=false`  
- **When** initiate  
- **Then**: no exception thrown; service still returns success; log warning.

### 3.7 Parent not found
- **Given** repository reports no user  
- **When** initiate  
- **Then**: `ResponseStatusException 404`.

### 3.8 Contact normalization
- **Given** mixed‑case email / trimmed phone  
- **When** initiate twice with variants  
- **Then**: one DB record (idempotent), indicating hash equality.

### 3.9 Unsupported method guard
- **Given** `verificationMethod` not EMAIL/SMS  
- **When** initiate  
- **Then**: `400` with “Unsupported verification method”.

### 3.10 Clock usage
- **Given** fixed `Clock`  
- **Then**: `expiresAt` and response timestamps are deterministic.

### 3.11 Boundary: TTL exact edge
- **Given** existing record with `expiresAt == now`  
- **When** initiate  
- **Then**: treated as **expired** (query uses `expiresAt > now`), so **new** record is created.

### 3.12 After-commit failure insulation
- **Given** email send throws after commit  
- **Then**: no exception bubbles to caller; DB state remains saved; logs contain masked email.

---

## 4) Repository Tests (`ParentVerificationRepository`)

### 4.1 `findPendingForParentMethodContact`
- Returns record only when `status=PENDING` and `expiresAt > now` and exact method+hash match.

### 4.2 `findPendingVerificationsByParent`
- Orders by `createdAt DESC` and filters `PENDING` & unexpired.

### 4.3 Unique constraint behavior
- Duplicate insert of `(parentId, method, contactHash, PENDING)` under concurrency fails once with `DataIntegrityViolationException`.

### 4.4 Index usage / query correctness
- For large dataset, query returns expected subset; (optional) explain-plan in integration DB.

### 4.5 Byte array equality in JPQL
- Ensure behavior across target DB (e.g., MySQL) matches H2; add profile that runs against real DB container.

### 4.6 Expired pending query
- `findExpiredPendingVerifications(now)` returns items with `expiresAt <= now`.

---

## 5) EmailService Tests (`EmailServiceImpl`)

### 5.1 sendVerificationEmail content
- Asserts subject and body contain code and TTL; sender equals config; logs mask recipient.

### 5.2 disabled email
- With `enabled=false`, method exits early with warning; no `mailSender.send` call.

### 5.3 failure path
- `mailSender.send` throws → service throws `RuntimeException` (for verification), while password confirmation path swallows (by design).

### 5.4 Masking helper
- `maskEmail` returns `***@domain` for typical addresses and safe fallback for malformed inputs.

---

## 6) Concurrency Tests

### 6.1 Parallel initiations (same parent/method/contact)
- N threads call initiate simultaneously → exactly one **created**; others **reuse** after conflict path; no duplicates in DB.

### 6.2 Mixed contacts
- Parallel initiations with different contacts produce separate records.

---

## 7) Security / Abuse / Rate-Limiting (present or placeholders)

### 7.1 (If implemented) per-parent attempt throttling
- Simulate burst calls → `429` when threshold hit; success below threshold.

### 7.2 No PII leakage
- Ensure logs contain masked emails only; no raw contact info in errors or responses.

### 7.3 Headers
- All responses include `Cache-Control: no-store`, `Pragma: no-cache`, `Expires: 0`; verify presence and values.

---

## 8) Contract / Serialization Tests

### 8.1 Response shape
- `VerificationStatusResponse` fields present; enums as strings; timestamps RFC3339 offset (`OffsetDateTime`).

### 8.2 OpenAPI conformance
- Generated OpenAPI matches DTO constraints (regex for code; UUID formats). Snapshot test to detect accidental changes.

---

## 9) Property/Fuzz Tests

### 9.1 Emails
- Random case permutations & whitespace → identical contact hash.

### 9.2 Phones
- Valid E.164 random numbers → accepted; invalid shapes → rejected by validator.

---

## 10) Performance / Smoke

### 10.1 Large history of verifications
- Insertion of many rows per parent; `initiate` remains fast (< configured budget); queries hit index (no full scans in explain).

---

## 11) Pending / Future tests (document now, implement later)

- **SMS dispatch integration** once provider added.  
- **Metrics/Tracing** counters & spans.  
- **Audit trail** entry writing if/when added.  
- **Attempt count** semantics when verifying codes (not part of initiate).

---

## Notes on Running
- Prefer running repository tests against the **target DB** (e.g., Testcontainers for MySQL) to verify `VARBINARY` behavior and unique constraints.  
- Use `@DirtiesContext` or separate transactions when asserting `afterCommit` behavior.  
- For concurrency tests, use `CountDownLatch`/`CyclicBarrier` and assert with eventual consistency (e.g., Awaitility).
