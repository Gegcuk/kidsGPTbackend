# Test Update Plan - @GeneratedValue Annotations Fix

## Current Status (2025-08-04)

**Test Results Summary:**
- **Total Tests Run**: 571
- **Failures**: 46
- **Errors**: 0
- **Success Rate**: ~92%

## Current Failing Tests

### Integration Tests with HTTP 500 Errors (46 failures)

#### 1. ConsentControllerIntegrationTest (32 failures)
**Problem**: All grantConsent and withdrawConsent tests returning HTTP 500 instead of expected 200/201
**Root Cause**: Entity creation issues in test setup after adding `@GeneratedValue(strategy = GenerationType.UUID)` to `ParentVerification` and `ConsentLedger` entities
**Affected Methods**:
- `grantConsent_ShouldReturnConsentIdInHeader`
- `grantConsent_ValidRequest_ShouldReturnSuccess`
- `grantConsent_WithAllConsentSources_ShouldReturnSuccess`
- `grantConsent_WithAllConsentTypes_ShouldReturnSuccess`
- `grantConsent_WithAllLawfulBasis_ShouldReturnSuccess`
- `grantConsent_WithDifferentConsentTypes_ShouldCalculateDifferentRetention`
- `grantConsent_WithDuplicateKids_ShouldDeduplicateAndSucceed`
- `grantConsent_WithEmptyKidsList_ShouldReturnBadRequest`
- `grantConsent_WithNonUKJurisdiction_ShouldUseDefaultRetention`
- `grantConsent_WithNullKidsList_ShouldReturnBadRequest`
- `grantConsent_WithNullVerificationId_ShouldReturnSuccess`
- `grantConsent_WithParentalConsent_ShouldReturnSuccess`
- `grantConsent_WithPrivacyPolicy_ShouldClearKidsList`
- `grantConsent_WithSpecialCharactersInFields_ShouldReturnSuccess`
- `grantConsent_WithSubdomainPolicyUrl_ShouldReturnSuccess`
- `grantConsent_WithTermsOfService_ShouldClearKidsList`
- `grantConsent_WithTermsOfService_ShouldReturnSuccess`
- `grantConsent_WithUKJurisdiction_ShouldCalculateCorrectRetention`
- `grantConsent_WithUppercaseHost_ShouldReturnSuccess`
- `withdrawConsent_AllConsentTypes_ShouldSucceed`
- `withdrawConsent_ControllerHeaderParity_ShouldReturnXConsentIdHeader`
- `withdrawConsent_CrossTypeUnaffected_ShouldSucceed`
- `withdrawConsent_CurrentActiveVersion_ShouldSucceed`
- `withdrawConsent_GrantWithdrawGrantAgain_ShouldShowCorrectLineage`
- `withdrawConsent_IdempotentRetrySameVersion_ShouldReturnExistingWithdrawalId`
- `withdrawConsent_IpUaOverride_ShouldUseServerCapturedValues`
- `withdrawConsent_MissingContentType_ShouldReturnUnsupportedMediaType`
- `withdrawConsent_MultipleUsersIsolation_ShouldPreventCrosstalk`
- `withdrawConsent_NoActiveGrantForVersion_ShouldReturnNotFound`
- `withdrawConsent_NonCurrentVersion_ShouldReturnConflict`
- `withdrawConsent_ReasonOmissionCases_ShouldOmitReasonFromReceipt`
- `withdrawConsent_ResponseLatestByTypeReflectsWithdrawn`
- `withdrawConsent_TimestampSanity_ShouldBeWithinAcceptableDelta`
- `withdrawConsent_UnauthorizedForbidden_ShouldReturn401Or403`

#### 2. VerificationControllerIntegrationTest (14 failures)
**Problem**: All initiateVerification tests returning HTTP 500 instead of expected 201
**Root Cause**: Same entity creation issues in test setup
**Affected Methods**:
- `initiateVerification_combinedNormalization_producesIdenticalHash`
- `initiateVerification_emailCaseInsensitivity_producesIdenticalHash`
- `initiateVerification_emailValid_passes`
- `initiateVerification_emailWithWhitespaceAndMixedCase_normalizationWorks`
- `initiateVerification_newVerification_returns201WithProperHeaders`
- `initiateVerification_phoneE164Trimming_hashedIdentically`
- `initiateVerification_reuseExistingVerification_returns200WithSameHeaders`
- `initiateVerification_smsValidE164_passes`
- `initiateVerification_smsVerificationReuse_returns200WithSameHeaders`
- `initiateVerification_smsVerification_returns201WithProperHeaders`
- `initiateVerification_smsWithWhitespace_trimmingWorks`
- `initiateVerification_whitespaceTrimming_ignoredBeforeHashing`

## Root Cause Analysis

The failures are due to entity creation issues in test setup after adding `@GeneratedValue(strategy = GenerationType.UUID)` to `ParentVerification` and `ConsentLedger` entities. Tests are still trying to use manually assigned UUIDs instead of letting Hibernate auto-generate them.

## Fix Plan

### For Each Failing Test:
1. Remove all manual UUID assignments to entity IDs for affected entities
2. Save referenced entities first and use their generated IDs in referencing entities
3. Use `repository.save()` for persistence, not `entityManager.persist()`
4. Update assertions to use the actual generated IDs
5. Ensure all required fields are set properly

### Implementation Steps:
1. **ConsentControllerIntegrationTest**: Update test data setup to use repository.save() and auto-generated IDs
2. **VerificationControllerIntegrationTest**: Same approach - fix test data setup and ensure all required fields are set

## Expected Outcome
After implementing these fixes, all integration tests should return proper status codes (200/201) instead of HTTP 500 errors, bringing the test suite to 100% passing. 