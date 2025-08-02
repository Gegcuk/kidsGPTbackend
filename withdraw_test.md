# withdraw_test.md

Comprehensive test plan for the **/api/v1/consent/withdraw** endpoint and the `ConsentServiceImpl.withdrawConsent` implementation.

---

## Conventions
- **I** = Integration test (MVC + DB, through controller)  
- **U** = Service unit test (mock repositories, verify behavior)  
- **R** = Repository test (data-layer behavior)  
- Unless noted, happy-path tests expect `200 OK`, JSON body with fields, and, for controller tests, `X-Consent-Id` header containing the withdrawal ID.

---

## A. Happy-path coverage

1. **I — Withdraw current active version (basic happy-path)**  
   - Pre: one `GRANTED` record for user/type/version `V1`.  
   - Call `/withdraw` with matching `type` and `version=V1`.  
   - Assert: `200`, `X-Consent-Id` present; response `reconsentNeeded=true`; latest status for that type is `WITHDRAWN` with correct `version`, `policyUrl`, and `timestamp`.

2. **I — All consent types happy-path**  
   - Repeat (1) for each `ConsentType` (`TERMS_OF_SERVICE`, `PRIVACY_POLICY`, `PARENTAL_CONSENT`, `DATA_PROCESSING`).  
   - Assert same outcomes.

3. **I — Cross-type unaffected**  
   - Pre: multiple `GRANTED` rows across different types.  
   - Withdraw one type.  
   - Assert: the withdrawn type is `WITHDRAWN`; all other types remain `GRANTED` in `latestByType`.

4. **U — Withdrawal persists correct ledger fields**  
   - Verify persisted `ConsentLedger` for withdrawal has:  
     `consentStatus=WITHDRAWN`, `withdrawnConsentId` referencing the grant, `consentVersion` = **grant’s version**, `policyUrl/contentHash/jurisdiction/region/locale/lawfulBasis/source` copied from grant, `retentionExpiresAt` unchanged.

5. **U — Receipt JSON (withdrawal) content**  
   - Ensure `receiptJson` contains:
     - `consent_id` (new withdrawal ID)  
     - `parent_uuid`  
     - `withdrawn_consent_id` (grant ID)  
     - `consent_type`  
     - `consent_version` (grant’s version)  
     - `policy_url`, `content_hash`  
     - `jurisdiction`, `region`, `locale`  
     - `lawful_basis`, `source`  
     - `timestamp`, `ip`, `ua`  
     - `action: "WITHDRAWN"`  
   - And **includes** `reason` only when provided (see negative variants below).

6. **U — HMAC signature present**  
   - After building receipt JSON, `recordSignature` is non-null and non-empty.

7. **U — Base64 HMAC key path**  
   - Configure `hmacSecret` as a valid Base64 string.  
   - Assert `recordSignature` is still produced (exercises Base64 branch).

8. **I — IP/UA override (audit)**  
   - Provide client `ipAddress`/`userAgent` in request, set server-captured IP/UA via request context.  
   - Assert persisted withdrawal row uses **server-captured** IP/UA (not client-provided).

9. **U — No child coverage writes on withdrawal**  
   - Ensure no calls to `ConsentChildCoverageRepository.saveAll` are made during withdrawal (coverage is not mutated).

---

## B. Idempotency & ordering

10. **I — Idempotent retry same version**  
    - Two sequential `/withdraw` calls for same user/type/version `V1`.  
    - First creates withdrawal; second returns `200` with **existing** withdrawal ID (not a new row).

11. **U — Duplicate-key race handling**  
    - Mock `saveAndFlush` to throw duplicate-key `DataIntegrityViolationException`.  
    - Service should return existing withdrawal ID from repository instead of failing.

12. **U — Non-duplicate DataIntegrityViolation → 500**  
    - Mock `saveAndFlush` to throw a **non-duplicate** `DataIntegrityViolationException`.  
    - Expect `ResponseStatusException(500)`.

13. **U — Version-specific idempotency correctness (multi-version)**  
    - Grant `V1`, withdraw `V1` (WITHDRAWN exists). Grant `V2`, withdraw `V2`.  
    - A subsequent withdraw for `V1` returns **V1**’s existing withdrawal (does not accidentally return the latest withdrawal for `V2`).  
    - _Note_: Prefer a version-scoped WITHDRAWN finder; if not present, verify logic still returns the correct ID.

14. **U — Effective status uses consentTimestamp ordering**  
    - Create two records for the same type with out-of-order `createdAt` but increasing `consentTimestamp`.  
    - Assert `buildEffectiveConsentStatus` chooses the entry with the **latest consentTimestamp**.

---

## C. Version selection / conflict semantics

15. **I — Not found when no active grant for version**  
    - No `GRANTED` record for the provided version.  
    - Expect `404` with meaningful error message.

16. **I — Conflict when attempting to withdraw non-current version**  
    - Have `GRANTED` rows for `V1` (older) and `V2` (latest).  
    - Withdraw `V1` → expect `409 Conflict` with explanatory message.

17. **U — Grant vs request version source of truth**  
    - For a valid withdraw, assert persisted row’s version equals the **grant’s** version (even if request attempted to pass a different string—defensive check).

---

## D. Validation & error handling

18. **I — Invalid userId format**  
    - `userId` not a UUID.  
    - Expect `400 Bad Request` (from service) with helpful message.

19. **I — Missing/blank version → Bean Validation**  
    - Omit or send blank `consentVersion`.  
    - Expect `400 Bad Request` due to `@NotBlank` on DTO (controller-level validation).

20. **I — Missing consentType → Bean Validation**  
    - `consentType=null`.  
    - Expect `400 Bad Request` due to `@NotNull` on DTO.

21. **I — Reason omission cases**  
    - `reason=null` and `reason=""`.  
    - Assert withdrawal `receiptJson` **omits** `reason` key.

22. **I — Controller header parity**  
    - Ensure `/withdraw` returns `X-Consent-Id` header mirroring body `consentId` (parity with `/grant`).

23. **I — Content type enforced**  
    - POST without `Content-Type: application/json` → expect `415 Unsupported Media Type` (depending on global MVC config).

24. **I — Unauthorized / Forbidden (if security enabled)**  
    - No/invalid token → `401`.  
    - Authenticated as different user (if applicable) → `403`.  
    - (If security is not implemented yet, mark these as **pending**.)

---

## E. Response shape & semantics

25. **I — Response latestByType reflects WITHDRAWN**  
    - After withdrawal, `latestByType` includes an entry for the target type with:  
      - `status=WITHDRAWN`  
      - `version` equals the withdrawn version  
      - `policyUrl` from grant  
      - `timestamp` close to “now”.

26. **U — reconsentNeeded flag**  
    - Assert `reconsentNeeded=true` for successful withdrawals.

27. **U — Locale and region continuity**  
    - Withdraw row and response reflect **same locale/region** as the grant (no mutation).

28. **U — Parent verification continuity (where applicable)**  
    - For `PARENTAL_CONSENT`, verify withdrawal row keeps the `parentVerificationId` from the grant.

---

## F. Repository-level behavior

29. **R — findActiveGrantByUserTypeAndVersion**  
    - Insert multiple grants and withdrawals; verify this query returns **only the GRANTED** row for the exact version.

30. **R — existsWithdrawalByUserTypeAndVersion**  
    - For user/type/version combos with and without withdrawals, ensure boolean accuracy.

31. **R — findFirstByUserIdAndConsentTypeOrderByConsentTimestampDesc**  
    - Insert multiple rows with varying timestamps; verify correct record is returned.

32. **R — Duplicate-key simulation guidance** (optional)  
    - Depending on DB, simulate concurrent insert uniqueness for the same `(userId, type, consentStatus=WITHDRAWN, consentVersion)` if you enforce such a constraint.  
    - Validate the service’s duplicate-key path.

---

## G. Observability / auditing (optional but valuable)

33. **U — Log message when IP/UA overridden**  
    - Verify a log line is emitted when client IP/UA differs from server-captured. (Use a log appender in unit tests if you enforce logging expectations.)

34. **I — Timestamp sanity**  
    - `consentTimestamp` of withdrawal is within an acceptable delta of system time.

---

## H. End-to-end scenarios (broader flows)

35. **I — Grant → Withdraw → Grant again**  
    - Grant `V1`, withdraw `V1`, then grant `V2`.  
    - Assert historical lineage: `WITHDRAWN` for `V1`, `GRANTED` for `V2`, effective status shows `GRANTED (V2)`; `/withdraw` for `V1` remains idempotent.

36. **I — Multiple users isolation**  
    - Parallel data for User A and B. Withdraw A’s consent.  
    - Assert no leakage/crosstalk: effective statuses and counts are isolated per user.

---

### Notes
- If you introduce a version-scoped WITHDRAWN finder (recommended), add a repository test to guarantee it returns the correct record for `(user,type,version,WITHDRAWN)`; use it in idempotency paths to avoid “latest of any version” mistakes.
- Security-related tests depend on your actual Spring Security configuration; include them if authZ/authN is enforced at the controller layer.
