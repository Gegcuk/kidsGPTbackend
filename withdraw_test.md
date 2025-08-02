# withdraw_test.md

Comprehensive test plan for the **/api/v1/consent/withdraw** endpoint and the `ConsentServiceImpl.withdrawConsent` implementation.

---

## Conventions
- **I** = Integration test (MVC + DB, through controller)  
- **U** = Service unit test (mock repositories, verify behavior)  
- **R** = Repository test (data-layer behavior)  
- Unless noted, happy-path tests expect `200 OK`, JSON body with fields, and, for controller tests, `X-Consent-Id` header containing the withdrawal ID.

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
