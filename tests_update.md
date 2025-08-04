# Test Update Plan - @GeneratedValue Annotations Fix

## Summary
After adding `@GeneratedValue(strategy = GenerationType.UUID)` to `ParentVerification` and `ConsentLedger` entities, multiple test failures occurred. This document outlines the issues and the plan to fix them.

## Issues Identified

### 1. EntityExists Errors (66 errors)
**Problem**: Tests are getting `EntityExists detached entity passed to persist` errors
**Root Cause**: Tests are trying to persist entities with manually assigned UUIDs, but now the IDs are auto-generated
**Affected Tests**: All ConsentLedger repository tests

### 2. ObjectOptimisticLockingFailure Errors (20 errors)
**Problem**: `ObjectOptimisticLockingFailure Row was updated or deleted by another transaction`
**Root Cause**: Concurrent access to entities during test setup
**Affected Tests**: ConsentHistory integration tests

### 3. 500 Errors in Integration Tests (46 failures)
**Problem**: Integration tests returning 500 instead of expected 200/201 status codes
**Root Cause**: Likely related to ID generation changes affecting entity creation
**Affected Tests**: ConsentController and VerificationController integration tests

## Detailed Fix Plan

### Phase 1: Fix EntityExists Errors in Repository Tests

#### 1.1 ConsentLedger Repository Tests
**Files to Update**:
- `ConsentLedgerActiveGrantRepositoryTest.java`
- `ConsentLedgerCountAndFilterRepositoryTest.java`
- `ConsentLedgerEntityBehaviorRepositoryTest.java`
- `ConsentLedgerExpiredConsentsRepositoryTest.java`
- `ConsentLedgerFindFirstRepositoryTest.java`
- `ConsentLedgerOrderingRepositoryTest.java`
- `ConsentLedgerPaginationRepositoryTest.java`
- `ConsentLedgerWithdrawalRepositoryTest.java`

**Changes Required**:
1. Remove manual UUID assignment in test setup
2. Let Hibernate auto-generate IDs
3. Update assertions to check for non-null IDs instead of specific UUIDs
4. Use `entityManager.flush()` and `entityManager.clear()` to ensure proper persistence

**Example Fix Pattern**:
```java
// Before
ConsentLedger ledger = ConsentLedger.builder()
    .consentId(UUID.randomUUID()) // Remove this line
    .userId(testUserId)
    // ... other fields
    .build();

// After
ConsentLedger ledger = ConsentLedger.builder()
    .userId(testUserId)
    // ... other fields
    .build();

ConsentLedger savedLedger = consentLedgerRepository.save(ledger);
assertNotNull(savedLedger.getConsentId()); // Check ID was generated
```

#### 1.2 ParentVerification Repository Tests
**Files to Update**:
- `ParentVerificationRepositoryTest.java`

**Changes Required**:
1. Remove manual UUID assignment for `verificationId`
2. Update test assertions to verify auto-generated IDs
3. Ensure proper entity lifecycle management

### Phase 2: Fix ObjectOptimisticLockingFailure Errors

#### 2.1 ConsentHistory Integration Tests
**Files to Update**:
- `ConsentHistoryCoverageDuplicatesIntegrationTest.java`
- `ConsentHistoryNullFieldsIntegrationTest.java`
- `ConsentHistoryOrderingIntegrationTest.java`
- `ConsentHistoryPerformanceIntegrationTest.java`
- `ConsentHistoryWithdrawnGrantedIntegrationTest.java`

**Changes Required**:
1. Add proper transaction management
2. Use `@Transactional` annotations where missing
3. Implement proper cleanup between tests
4. Use `@DirtiesContext` if needed for test isolation

**Example Fix Pattern**:
```java
@Test
@Transactional
@DirtiesContext
void testMethod() {
    // Test implementation
}
```

### Phase 3: Fix Integration Test 500 Errors

#### 3.1 ConsentController Integration Tests
**Files to Update**:
- `ConsentControllerIntegrationTest.java`

**Changes Required**:
1. Update test setup to not rely on specific UUIDs
2. Ensure proper entity creation flow
3. Update assertions to handle auto-generated IDs
4. Check for proper error handling in service layer

#### 3.2 VerificationController Integration Tests
**Files to Update**:
- `VerificationControllerIntegrationTest.java`

**Changes Required**:
1. Update ParentVerification creation to not set manual IDs
2. Ensure proper verification flow with auto-generated IDs
3. Update response assertions

### Phase 4: Service Layer Updates

#### 4.1 ConsentService Updates
**Files to Update**:
- `ConsentServiceImpl.java`

**Changes Required**:
1. Ensure proper handling of auto-generated IDs
2. Update any logic that depends on specific UUID patterns
3. Verify foreign key relationships work correctly

#### 4.2 ParentVerificationService Updates
**Files to Update**:
- `ParentVerificationServiceImpl.java`

**Changes Required**:
1. Update verification creation logic
2. Ensure proper ID handling in verification flow

## Implementation Priority

### High Priority (Fix First)
1. **ConsentLedger Repository Tests** - EntityExists errors
   - ✅ **ConsentLedgerActiveGrantRepositoryTest.java** - FIXED
   - ✅ **ConsentLedgerWithdrawalRepositoryTest.java** - FIXED
   - ✅ **ConsentLedgerPaginationRepositoryTest.java** - FIXED
   - ✅ **ConsentLedgerOrderingRepositoryTest.java** - FIXED
   - ✅ **ConsentLedgerFindFirstRepositoryTest.java** - FIXED
   - ✅ **ConsentLedgerExpiredConsentsRepositoryTest.java** - FIXED
   - ✅ **ConsentLedgerEntityBehaviorRepositoryTest.java** - FIXED
   - ⏳ **ConsentLedgerCountAndFilterRepositoryTest.java** - PENDING
2. **ParentVerification Repository Tests** - EntityExists errors

### Medium Priority
3. **ConsentHistory Integration Tests** - ObjectOptimisticLockingFailure errors
4. **Service Layer Updates** - Ensure proper ID handling

### Low Priority
5. **Integration Test 500 Errors** - May resolve after repository fixes

## Testing Strategy

### Step 1: Fix Repository Tests
- Run individual repository test classes
- Verify each test passes in isolation
- Use: `mvn test -Dtest=*RepositoryTest`

### Step 2: Fix Integration Tests
- Run integration tests after repository fixes
- Use: `mvn test -Dtest=*IntegrationTest`

### Step 3: Full Test Suite
- Run complete test suite
- Use: `mvn test`

## Expected Outcomes

After implementing these fixes:
1. All EntityExists errors should be resolved
2. ObjectOptimisticLockingFailure errors should be eliminated
3. Integration tests should return proper status codes
4. Auto-generated UUIDs should work correctly throughout the application

## Rollback Plan

If issues persist, consider:
1. Reverting the `@GeneratedValue` annotations
2. Implementing manual ID generation in service layer
3. Using a different ID generation strategy

## Notes

- The `@GeneratedValue(strategy = GenerationType.UUID)` annotation is the correct approach for UUID primary keys
- Test failures are expected when changing ID generation strategy
- Most fixes involve removing manual ID assignment and letting Hibernate handle generation
- Proper transaction management is crucial for test stability 

## Current Status After Adding @GeneratedValue(strategy = GenerationType.UUID)

After adding `@GeneratedValue(strategy = GenerationType.UUID)` to `ParentVerification` and `ConsentLedger` entities, multiple test failures occurred. This document outlines the issues and the plan to fix them.

### Summary of Current Test Failures (from latest `mvn test`)

- **ConsentControllerIntegrationTest** (many grantConsent/withdrawConsent tests): HTTP 500 errors
- **VerificationControllerIntegrationTest** (initiateVerification tests): HTTP 500 errors
- **ConsentLedgerCountAndFilterRepositoryTest**: ObjectOptimisticLockingFailure
- **ConsentHistoryCoverageDuplicatesIntegrationTest**: ObjectOptimisticLockingFailure
- **ConsentHistoryNullFieldsIntegrationTest**: ObjectOptimisticLockingFailure
- **ConsentHistoryOrderingIntegrationTest**: ObjectOptimisticLockingFailure
- **ConsentHistoryPerformanceIntegrationTest**: ObjectOptimisticLockingFailure
- **ConsentHistoryWithdrawnGrantedIntegrationTest**: ObjectOptimisticLockingFailure

#### Error Types
- `ObjectOptimisticLockingFailureException`: Row was updated or deleted by another transaction (or unsaved-value mapping was incorrect)
- HTTP 500 errors in controller/integration tests (likely due to repository/entity issues)
- DataIntegrityViolationException: Foreign key constraint violation (in some withdrawal tests)

---

## Plan to Fix Remaining Test Failures

### 1. **Repository/Entity Layer**
- [ ] **ConsentLedgerCountAndFilterRepositoryTest**: Fix all tests referencing other ConsentLedger records (e.g., withdrawnConsentId, parentVerificationId) to use the auto-generated ID from the saved entity. Save referenced entities first, then use their IDs in referencing entities.
- [ ] **ConsentHistoryCoverageDuplicatesIntegrationTest**: Same pattern—ensure referenced ConsentLedger records are saved and IDs are used correctly.
- [ ] **ConsentHistoryNullFieldsIntegrationTest**: Same as above.
- [ ] **ConsentHistoryOrderingIntegrationTest**: Same as above.
- [ ] **ConsentHistoryPerformanceIntegrationTest**: Same as above.
- [ ] **ConsentHistoryWithdrawnGrantedIntegrationTest**: Same as above.

### 2. **Controller/Integration Layer**
- [ ] **ConsentControllerIntegrationTest**: Investigate root cause of HTTP 500 errors. Likely due to repository/entity changes. Fix by ensuring all test data setup uses repository.save() and auto-generated IDs, and that all required fields are set.
- [ ] **VerificationControllerIntegrationTest**: Same as above—fix test data setup and ensure all required fields are set, using repository.save() and auto-generated IDs.

### 3. **General Steps for Each Failing Test**
1. Remove all manual UUID assignments to entity IDs for affected entities.
2. Save referenced entities first and use their generated IDs in referencing entities.
3. Use repository.save() for persistence, not entityManager.persist().
4. Update assertions to use the actual generated IDs.
5. For controller/integration tests, ensure all test data setup is compatible with the new entity ID generation.

---

### Progress Tracking
- [ ] ConsentLedgerCountAndFilterRepositoryTest - IN PROGRESS
- [ ] ConsentHistoryCoverageDuplicatesIntegrationTest - PENDING
- [ ] ConsentHistoryNullFieldsIntegrationTest - PENDING
- [ ] ConsentHistoryOrderingIntegrationTest - PENDING
- [ ] ConsentHistoryPerformanceIntegrationTest - PENDING
- [ ] ConsentHistoryWithdrawnGrantedIntegrationTest - PENDING
- [ ] ConsentControllerIntegrationTest - PENDING
- [ ] VerificationControllerIntegrationTest - PENDING

---

**Note:** All tests worked before the entity changes. The above plan will systematically restore test stability by aligning test data setup and persistence logic with the new auto-generated UUID strategy. 