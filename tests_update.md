# Test Failures Analysis and Resolution Plan

## Current Test Status (Latest Run)
**Summary: 18 failures, 3 errors**

### 1. ConsentControllerIntegrationTest Failures (11 failures)
All failures show `Status expected:<200> but was:<500>`

**Failed Tests:**
- `withdrawConsent_AllConsentTypes_ShouldSucceed`
- `withdrawConsent_ControllerHeaderParity_ShouldReturnXConsentIdHeader`
- `withdrawConsent_CrossTypeUnaffected_ShouldSucceed`
- `withdrawConsent_CurrentActiveVersion_ShouldSucceed`
- `withdrawConsent_GrantWithdrawGrantAgain_ShouldShowCorrectLineage`
- `withdrawConsent_IdempotentRetrySameVersion_ShouldReturnExistingWithdrawalId`
- `withdrawConsent_IpUaOverride_ShouldUseServerCapturedValues`
- `withdrawConsent_MultipleUsersIsolation_ShouldPreventCrosstalk`
- `withdrawConsent_ReasonOmissionCases_ShouldOmitReasonFromReceipt`
- `withdrawConsent_ResponseLatestByTypeReflectsWithdrawn`
- `withdrawConsent_TimestampSanity_ShouldBeWithinAcceptableDelta`

**Root Cause:** Integration tests are getting 500 errors instead of 200 OK responses, indicating the controller endpoints are throwing exceptions due to service layer issues.

### 2. ConsentGrantServiceTest Failures (7 failures)
Multiple assertion failures related to HMAC signature validation and receipt JSON validation.

**Failed Tests:**
- `grantConsent_ShouldCalculateRetentionBasedOnConsentType` - expected: not <null>
- `grantConsent_ShouldGenerateHmacSignature` - expected: <true> but was: <false>
- `grantConsent_ShouldGenerateValidReceiptJson` - expected: <true> but was: <false>
- `grantConsent_ShouldHandleSpecialCharactersInReceiptJson` - expected: <true> but was: <false>
- `grantConsent_ShouldHandleUnknownVerificationMethod` - expected: <true> but was: <false>
- `grantConsent_ShouldResolveVerificationMethod` - expected: <true> but was: <false>
- `grantConsent_ShouldSetRetentionExpiryDate` - expected: not <null>

**Root Cause:** Issues with retention calculation, verification method handling, and HMAC signature generation. Related to the double `saveAndFlush` pattern.

### 3. ConsentWithdrawServiceTest Errors (3 errors)
Mockito configuration issues with stubbing.

**Failed Tests:**
- `withdrawConsent_IpUaOverride_ShouldLogMessage` - `PotentialStubbingProblem`
- `withdrawConsent_LocaleAndRegionContinuity_ShouldPreserveGrantValues` - `UnnecessaryStubbingException`
- `withdrawConsent_ParentVerificationContinuity_ShouldPreserveVerificationId` - `UnnecessaryStubbingException`

**Root Cause:** Stubbing argument mismatch and unnecessary stubbings.

## Root Cause Analysis

### Core Issue: Service Method Calls All Consent Types
The fundamental problem is in `ConsentServiceImpl.buildEffectiveConsentStatus()`:

```java
private List<ConsentStatusResponse.ConsentStatusByType> buildEffectiveConsentStatus(UUID userId) {
    List<ConsentLedger> latestConsents = new ArrayList<>();
    
    for (ConsentType type : ConsentType.values()) { // Calls for ALL 4 types
        Optional<ConsentLedger> latestConsent = consentLedgerRepository
            .findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc(userId, type);
        latestConsent.ifPresent(latestConsents::add);
    }
    // ...
}
```

**ConsentType.values() includes:**
- `PRIVACY_POLICY`
- `TERMS_OF_SERVICE` 
- `PARENTAL_CONSENT`
- `DATA_PROCESSING`

### Test Setup Problem
1. **Service behavior:** `buildEffectiveConsentStatus()` calls the repository for all 4 consent types
2. **Test setup:** Many tests only stub the repository method for the specific consent type they're testing
3. **Mockito strictness:** Mockito fails when service calls repository with unstubbed arguments
4. **Integration failures:** 500 errors occur when service throws exceptions due to missing stubs

### Specific Error Patterns

#### PotentialStubbingProblem
- **Location:** `withdrawConsent_IpUaOverride_ShouldLogMessage` test
- **Issue:** Test stubs `findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc` for `DATA_PROCESSING` only
- **Service calls:** Repository for all 4 consent types including `PRIVACY_POLICY`
- **Result:** Mockito strict stubbing fails on unstubbed calls

#### UnnecessaryStubbingException
- **Location:** Multiple tests in `ConsentWithdrawServiceTest`
- **Issue:** Tests stub repository calls for consent types that aren't actually invoked
- **Result:** Mockito detects unused stubbings and fails

## Resolution Plan

### Phase 1: Fix Mockito Stubbing Issues (Priority: High)
**Target:** Resolve the 3 errors in `ConsentWithdrawServiceTest`

#### Step 1.1: Fix PotentialStubbingProblem
- **File:** `ConsentWithdrawServiceTest.java`
- **Test:** `withdrawConsent_IpUaOverride_ShouldLogMessage`
- **Action:** Add stubbing for all 4 consent types in the `thenAnswer` block
- **Code:** Stub `findFirstByUserIdAndConsentTypeOrderByConsentTimestampDescCreatedAtDesc` for `PRIVACY_POLICY`, `TERMS_OF_SERVICE`, `PARENTAL_CONSENT`, `DATA_PROCESSING`

#### Step 1.2: Remove Unnecessary Stubbings
- **Files:** `ConsentWithdrawServiceTest.java`
- **Tests:** `withdrawConsent_LocaleAndRegionContinuity_ShouldPreserveGrantValues`, `withdrawConsent_ParentVerificationContinuity_ShouldPreserveVerificationId`
- **Action:** Remove stubbings for consent types not actually called during test execution
- **Approach:** Use `lenient()` stubbing or remove unused `when()` calls

### Phase 2: Fix ConsentGrantServiceTest Failures (Priority: High)
**Target:** Resolve the 7 failures in `ConsentGrantServiceTest`

#### Step 2.1: Fix HMAC Signature Issues
- **Tests:** `grantConsent_ShouldGenerateHmacSignature`, `grantConsent_ShouldGenerateValidReceiptJson`
- **Issue:** HMAC signature validation failing
- **Action:** Verify HMAC secret configuration and signature generation logic
- **Check:** Ensure `app.consent.hmac.secret-ref` is properly configured in test properties

#### Step 2.2: Fix Retention Calculation Issues
- **Tests:** `grantConsent_ShouldCalculateRetentionBasedOnConsentType`, `grantConsent_ShouldSetRetentionExpiryDate`
- **Issue:** Retention expiry dates are null
- **Action:** Verify retention calculation logic and ensure proper date setting
- **Check:** Review `calculateRetentionYears()` method and retention expiry assignment

#### Step 2.3: Fix Verification Method Issues
- **Tests:** `grantConsent_ShouldHandleUnknownVerificationMethod`, `grantConsent_ShouldResolveVerificationMethod`
- **Issue:** Verification method resolution failing
- **Action:** Verify `resolveVerificationMethod()` method and parent verification repository stubbing
- **Check:** Ensure proper mocking of `ParentVerificationRepository`

#### Step 2.4: Fix Special Characters Handling
- **Test:** `grantConsent_ShouldHandleSpecialCharactersInReceiptJson`
- **Issue:** JSON serialization with special characters failing
- **Action:** Verify JSON escaping and special character handling in receipt generation
- **Check:** Review `buildCanonicalReceiptJson()` method

### Phase 3: Fix ConsentControllerIntegrationTest Failures (Priority: Medium)
**Target:** Resolve the 11 failures in `ConsentControllerIntegrationTest`

#### Step 3.1: Investigate 500 Errors
- **Issue:** All integration tests returning 500 instead of 200
- **Action:** Check controller exception handling and service layer exceptions
- **Approach:** 
  1. Review `GlobalExceptionHandler` for proper exception mapping
  2. Check if service exceptions are being caught and handled
  3. Verify database configuration in integration tests

#### Step 3.2: Fix Service Layer Integration
- **Issue:** Service layer throwing exceptions in integration context
- **Action:** Ensure proper repository stubbing in integration tests
- **Approach:** 
  1. Add comprehensive repository stubbing for all consent types
  2. Verify database schema and entity relationships
  3. Check transaction management in integration tests

### Phase 4: Comprehensive Test Review (Priority: Low)
**Target:** Prevent future similar issues

#### Step 4.1: Create Test Helper Methods
- **Action:** Create utility methods for consistent repository stubbing
- **Benefit:** Reduce duplication and ensure consistent test setup
- **Example:** `stubAllConsentTypesForUser(UUID userId)`

#### Step 4.2: Add Integration Test Base Class
- **Action:** Create base class with common integration test setup
- **Benefit:** Standardize integration test configuration
- **Include:** Database setup, repository stubbing, common assertions

#### Step 4.3: Review Mockito Configuration
- **Action:** Consider using `@MockitoSettings(strictness = Strictness.LENIENT)` for complex tests
- **Benefit:** Reduce strict stubbing issues while maintaining test quality

## Implementation Strategy

### Approach 1: Fix Tests to Match Service Behavior (Recommended)
- **Pros:** Maintains service behavior, fixes immediate issues
- **Cons:** Requires updating many test files
- **Effort:** Medium

### Approach 2: Modify Service to Query Only Relevant Types
- **Pros:** Reduces unnecessary database calls
- **Cons:** Changes service behavior, may affect other parts of the system
- **Effort:** High (requires careful analysis of all service usages)

### Approach 3: Use Lenient Mockito Configuration
- **Pros:** Quick fix for stubbing issues
- **Cons:** May hide real test problems
- **Effort:** Low

## Success Criteria
1. All 3 Mockito errors resolved
2. All 7 ConsentGrantServiceTest failures fixed
3. All 11 ConsentControllerIntegrationTest failures resolved
4. Total test failures reduced to 0
5. No regression in existing passing tests

## Next Steps
1. Start with Phase 1 (Mockito stubbing fixes)
2. Move to Phase 2 (ConsentGrantServiceTest fixes)
3. Address Phase 3 (Integration test fixes)
4. Implement Phase 4 improvements for future prevention