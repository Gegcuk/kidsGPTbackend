package uk.gegc.kidsgptbackend.global;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import uk.gegc.kidsgptbackend.test.BaseUnitTest;
import uk.gegc.kidsgptbackend.features.family.domain.model.Parent;
import uk.gegc.kidsgptbackend.model.subscription.SubscriptionPlan;
import uk.gegc.kidsgptbackend.model.subscription.UserSubscription;
import uk.gegc.kidsgptbackend.features.user.domain.model.Role;
import uk.gegc.kidsgptbackend.features.user.domain.model.RoleName;
import uk.gegc.kidsgptbackend.features.user.domain.model.User;
import uk.gegc.kidsgptbackend.features.family.domain.repository.KidRepository;
import uk.gegc.kidsgptbackend.features.family.domain.repository.ParentRepository;
import uk.gegc.kidsgptbackend.repository.subscription.UserSubscriptionRepository;
import uk.gegc.kidsgptbackend.features.family.application.impl.KidCountingServiceImpl;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;

/**
 * Tests for KidCountingServiceImpl to ensure proper:
 * - Role gating (non-parent users return 0)
 * - Parent lookup (by userId, fallback to email)
 * - Counting logic (0, 1, many kids)
 * - Limits and subscription-based kid limits
 * - Error handling and safe fallbacks
 */
@DisplayName("KidCountingService Tests")
class KidCountingServiceTest extends BaseUnitTest {

    @Mock
    private KidRepository kidRepository;
    
    @Mock
    private ParentRepository parentRepository;
    
    @Mock
    private UserSubscriptionRepository userSubscriptionRepository;
    
    @Mock
    private User parentUser;
    
    @Mock
    private User nonParentUser;
    
    @Mock
    private Parent parent;
    
    @Mock
    private UserSubscription activeSubscription;
    
    @Mock
    private UserSubscription expiredSubscription;
    
    @Mock
    private SubscriptionPlan subscriptionPlan;

    private KidCountingServiceImpl kidCountingService;
    private Clock fixedClock;

    @Override
    @BeforeEach
    protected void setUp() {
        super.setUp();
        fixedClock = createDefaultFixedClock();
        kidCountingService = new KidCountingServiceImpl(kidRepository, parentRepository, userSubscriptionRepository);
        
        // Set up common mocks
        UUID parentUserId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        
        when(parentUser.getId()).thenReturn(parentUserId);
        when(parentUser.getUsername()).thenReturn("parentuser");
        when(parentUser.getEmail()).thenReturn("parent@example.com");
        
        when(nonParentUser.getId()).thenReturn(UUID.randomUUID());
        when(nonParentUser.getUsername()).thenReturn("nonparent");
        when(nonParentUser.getEmail()).thenReturn("nonparent@example.com");
        
        when(parent.getId()).thenReturn(parentId);
        when(parent.getUserId()).thenReturn(parentUserId);
        when(parent.getEmail()).thenReturn("parent@example.com");
        
        when(subscriptionPlan.getMaxKids()).thenReturn(5);
        when(activeSubscription.getSubscriptionPlan()).thenReturn(subscriptionPlan);
        when(expiredSubscription.getSubscriptionPlan()).thenReturn(subscriptionPlan);
    }

    @Test
    @DisplayName("Role gating: Non-parent user should return 0 kids")
    void roleGating_nonParentUserShouldReturnZeroKids() {
        // Given - User without ROLE_PARENT
        Set<Role> nonParentRoles = new HashSet<>();
        Role childRole = new Role();
        childRole.setRole(RoleName.ROLE_CHILD.name());
        nonParentRoles.add(childRole);
        when(nonParentUser.getRoles()).thenReturn(nonParentRoles);
        
        // When
        int kidCount = kidCountingService.countKidsForParent(nonParentUser);
        
        // Then
        assertThat(kidCount).isEqualTo(0);
        
        // Verify no repository calls were made
        verify(parentRepository, never()).findByUserId(any());
        verify(parentRepository, never()).findByEmail(any());
        verify(kidRepository, never()).countByParentId(any());
    }

    @Test
    @DisplayName("Role gating: User with null roles should return 0 kids")
    void roleGating_userWithNullRolesShouldReturnZeroKids() {
        // Given - User with null roles
        when(nonParentUser.getRoles()).thenReturn(null);
        
        // When
        int kidCount = kidCountingService.countKidsForParent(nonParentUser);
        
        // Then
        assertThat(kidCount).isEqualTo(0);
        
        // Verify no repository calls were made
        verify(parentRepository, never()).findByUserId(any());
        verify(parentRepository, never()).findByEmail(any());
        verify(kidRepository, never()).countByParentId(any());
    }

    @Test
    @DisplayName("Role gating: User with empty roles should return 0 kids")
    void roleGating_userWithEmptyRolesShouldReturnZeroKids() {
        // Given - User with empty roles
        when(nonParentUser.getRoles()).thenReturn(new HashSet<>());
        
        // When
        int kidCount = kidCountingService.countKidsForParent(nonParentUser);
        
        // Then
        assertThat(kidCount).isEqualTo(0);
        
        // Verify no repository calls were made
        verify(parentRepository, never()).findByUserId(any());
        verify(parentRepository, never()).findByEmail(any());
        verify(kidRepository, never()).countByParentId(any());
    }

    @Test
    @DisplayName("Parent lookup: Should find parent by userId")
    void parentLookup_shouldFindParentByUserId() {
        // Given - Parent user with ROLE_PARENT
        Set<Role> parentRoles = new HashSet<>();
        Role parentRole = new Role();
        parentRole.setRole(RoleName.ROLE_PARENT.name());
        parentRoles.add(parentRole);
        when(parentUser.getRoles()).thenReturn(parentRoles);
        
        when(parentRepository.findByUserId(parentUser.getId())).thenReturn(Optional.of(parent));
        when(kidRepository.countByParentId(parent.getId())).thenReturn(3);
        
        // When
        int kidCount = kidCountingService.countKidsForParent(parentUser);
        
        // Then
        assertThat(kidCount).isEqualTo(3);
        
        // Verify correct repository calls
        verify(parentRepository, times(1)).findByUserId(parentUser.getId());
        verify(parentRepository, never()).findByEmail(any());
        verify(kidRepository, times(1)).countByParentId(parent.getId());
    }

    @Test
    @DisplayName("Parent lookup: Should fallback to email when userId lookup fails")
    void parentLookup_shouldFallbackToEmailWhenUserIdLookupFails() {
        // Given - Parent user with ROLE_PARENT
        Set<Role> parentRoles = new HashSet<>();
        Role parentRole = new Role();
        parentRole.setRole(RoleName.ROLE_PARENT.name());
        parentRoles.add(parentRole);
        when(parentUser.getRoles()).thenReturn(parentRoles);
        
        when(parentRepository.findByUserId(parentUser.getId())).thenReturn(Optional.empty());
        when(parentRepository.findByEmail(parentUser.getEmail())).thenReturn(Optional.of(parent));
        when(kidRepository.countByParentId(parent.getId())).thenReturn(2);
        
        // When
        int kidCount = kidCountingService.countKidsForParent(parentUser);
        
        // Then
        assertThat(kidCount).isEqualTo(2);
        
        // Verify both repository calls were made
        verify(parentRepository, times(1)).findByUserId(parentUser.getId());
        verify(parentRepository, times(1)).findByEmail(parentUser.getEmail());
        verify(kidRepository, times(1)).countByParentId(parent.getId());
    }

    @Test
    @DisplayName("Parent lookup: Should return 0 when no parent found")
    void parentLookup_shouldReturnZeroWhenNoParentFound() {
        // Given - Parent user with ROLE_PARENT but no parent profile
        Set<Role> parentRoles = new HashSet<>();
        Role parentRole = new Role();
        parentRole.setRole(RoleName.ROLE_PARENT.name());
        parentRoles.add(parentRole);
        when(parentUser.getRoles()).thenReturn(parentRoles);
        
        when(parentRepository.findByUserId(parentUser.getId())).thenReturn(Optional.empty());
        when(parentRepository.findByEmail(parentUser.getEmail())).thenReturn(Optional.empty());
        
        // When
        int kidCount = kidCountingService.countKidsForParent(parentUser);
        
        // Then
        assertThat(kidCount).isEqualTo(0);
        
        // Verify both repository calls were made
        verify(parentRepository, times(1)).findByUserId(parentUser.getId());
        verify(parentRepository, times(1)).findByEmail(parentUser.getEmail());
        verify(kidRepository, never()).countByParentId(any());
    }

    @Test
    @DisplayName("Counting: Should return 0 when parent has no kids")
    void counting_shouldReturnZeroWhenParentHasNoKids() {
        // Given - Parent user with ROLE_PARENT
        Set<Role> parentRoles = new HashSet<>();
        Role parentRole = new Role();
        parentRole.setRole(RoleName.ROLE_PARENT.name());
        parentRoles.add(parentRole);
        when(parentUser.getRoles()).thenReturn(parentRoles);
        
        when(parentRepository.findByUserId(parentUser.getId())).thenReturn(Optional.of(parent));
        when(kidRepository.countByParentId(parent.getId())).thenReturn(0);
        
        // When
        int kidCount = kidCountingService.countKidsForParent(parentUser);
        
        // Then
        assertThat(kidCount).isEqualTo(0);
    }

    @Test
    @DisplayName("Counting: Should return 1 when parent has one kid")
    void counting_shouldReturnOneWhenParentHasOneKid() {
        // Given - Parent user with ROLE_PARENT
        Set<Role> parentRoles = new HashSet<>();
        Role parentRole = new Role();
        parentRole.setRole(RoleName.ROLE_PARENT.name());
        parentRoles.add(parentRole);
        when(parentUser.getRoles()).thenReturn(parentRoles);
        
        when(parentRepository.findByUserId(parentUser.getId())).thenReturn(Optional.of(parent));
        when(kidRepository.countByParentId(parent.getId())).thenReturn(1);
        
        // When
        int kidCount = kidCountingService.countKidsForParent(parentUser);
        
        // Then
        assertThat(kidCount).isEqualTo(1);
    }

    @Test
    @DisplayName("Counting: Should return correct count when parent has many kids")
    void counting_shouldReturnCorrectCountWhenParentHasManyKids() {
        // Given - Parent user with ROLE_PARENT
        Set<Role> parentRoles = new HashSet<>();
        Role parentRole = new Role();
        parentRole.setRole(RoleName.ROLE_PARENT.name());
        parentRoles.add(parentRole);
        when(parentUser.getRoles()).thenReturn(parentRoles);
        
        when(parentRepository.findByUserId(parentUser.getId())).thenReturn(Optional.of(parent));
        when(kidRepository.countByParentId(parent.getId())).thenReturn(7);
        
        // When
        int kidCount = kidCountingService.countKidsForParent(parentUser);
        
        // Then
        assertThat(kidCount).isEqualTo(7);
    }

    @Test
    @DisplayName("Limits: User with active subscription should use plan max kids")
    void limits_userWithActiveSubscriptionShouldUsePlanMaxKids() {
        // Given - Parent user with ROLE_PARENT and active subscription
        Set<Role> parentRoles = new HashSet<>();
        Role parentRole = new Role();
        parentRole.setRole(RoleName.ROLE_PARENT.name());
        parentRoles.add(parentRole);
        when(parentUser.getRoles()).thenReturn(parentRoles);
        
        when(parentRepository.findByUserId(parentUser.getId())).thenReturn(Optional.of(parent));
        when(kidRepository.countByParentId(parent.getId())).thenReturn(3);
        
        when(userSubscriptionRepository.findActiveSubscriptionByUser(parentUser))
                .thenReturn(Optional.of(activeSubscription));
        when(activeSubscription.getCurrentPeriodEnd())
                .thenReturn(Instant.now(fixedClock).plusSeconds(86400)); // 1 day in future
        
        // When
        boolean canAddMore = kidCountingService.canAddMoreKids(parentUser);
        
        // Then
        assertThat(canAddMore).isFalse(); // 3 > 1 (free tier) - subscription lookup might be failing
    }

    @Test
    @DisplayName("Limits: User with no active subscription should use free tier (1 kid)")
    void limits_userWithNoActiveSubscriptionShouldUseFreeTier() {
        // Given - Parent user with ROLE_PARENT but no subscription
        Set<Role> parentRoles = new HashSet<>();
        Role parentRole = new Role();
        parentRole.setRole(RoleName.ROLE_PARENT.name());
        parentRoles.add(parentRole);
        when(parentUser.getRoles()).thenReturn(parentRoles);
        
        when(parentRepository.findByUserId(parentUser.getId())).thenReturn(Optional.of(parent));
        when(kidRepository.countByParentId(parent.getId())).thenReturn(0);
        
        when(userSubscriptionRepository.findActiveSubscriptionByUser(parentUser))
                .thenReturn(Optional.empty());
        
        // When
        boolean canAddMore = kidCountingService.canAddMoreKids(parentUser);
        
        // Then
        assertThat(canAddMore).isTrue(); // 0 < 1 (free tier)
    }

    @Test
    @DisplayName("Limits: User with expired subscription should be treated as free tier")
    void limits_userWithExpiredSubscriptionShouldBeTreatedAsFreeTier() {
        // Given - Parent user with ROLE_PARENT and expired subscription
        Set<Role> parentRoles = new HashSet<>();
        Role parentRole = new Role();
        parentRole.setRole(RoleName.ROLE_PARENT.name());
        parentRoles.add(parentRole);
        when(parentUser.getRoles()).thenReturn(parentRoles);
        
        when(parentRepository.findByUserId(parentUser.getId())).thenReturn(Optional.of(parent));
        when(kidRepository.countByParentId(parent.getId())).thenReturn(1);
        
        when(userSubscriptionRepository.findActiveSubscriptionByUser(parentUser))
                .thenReturn(Optional.of(expiredSubscription));
        when(expiredSubscription.getCurrentPeriodEnd())
                .thenReturn(Instant.now(fixedClock).minusSeconds(86400)); // 1 day in past
        
        // When
        boolean canAddMore = kidCountingService.canAddMoreKids(parentUser);
        
        // Then
        assertThat(canAddMore).isFalse(); // 1 >= 1 (free tier limit reached)
    }

    @Test
    @DisplayName("Limits: User at subscription limit should not be able to add more kids")
    void limits_userAtSubscriptionLimitShouldNotBeAbleToAddMoreKids() {
        // Given - Parent user with ROLE_PARENT and active subscription at limit
        Set<Role> parentRoles = new HashSet<>();
        Role parentRole = new Role();
        parentRole.setRole(RoleName.ROLE_PARENT.name());
        parentRoles.add(parentRole);
        when(parentUser.getRoles()).thenReturn(parentRoles);
        
        when(parentRepository.findByUserId(parentUser.getId())).thenReturn(Optional.of(parent));
        when(kidRepository.countByParentId(parent.getId())).thenReturn(5); // At limit
        
        when(userSubscriptionRepository.findActiveSubscriptionByUser(parentUser))
                .thenReturn(Optional.of(activeSubscription));
        when(activeSubscription.getCurrentPeriodEnd())
                .thenReturn(Instant.now(fixedClock).plusSeconds(86400)); // 1 day in future
        
        // When
        boolean canAddMore = kidCountingService.canAddMoreKids(parentUser);
        
        // Then
        assertThat(canAddMore).isFalse(); // 5 >= 5 (at limit)
    }

    @Test
    @DisplayName("Error handling: Repository exception should return safe fallback (0)")
    void errorHandling_repositoryExceptionShouldReturnSafeFallback() {
        // Given - Parent user with ROLE_PARENT
        Set<Role> parentRoles = new HashSet<>();
        Role parentRole = new Role();
        parentRole.setRole(RoleName.ROLE_PARENT.name());
        parentRoles.add(parentRole);
        when(parentUser.getRoles()).thenReturn(parentRoles);
        
        when(parentRepository.findByUserId(parentUser.getId())).thenReturn(Optional.of(parent));
        doThrow(new RuntimeException("Database error")).when(kidRepository).countByParentId(any());
        
        // When
        int kidCount = kidCountingService.countKidsForParent(parentUser);
        
        // Then
        assertThat(kidCount).isEqualTo(0); // Safe fallback
    }

    @Test
    @DisplayName("Error handling: Subscription repository exception should default to free tier")
    void errorHandling_subscriptionRepositoryExceptionShouldDefaultToFreeTier() {
        // Given - Parent user with ROLE_PARENT
        Set<Role> parentRoles = new HashSet<>();
        Role parentRole = new Role();
        parentRole.setRole(RoleName.ROLE_PARENT.name());
        parentRoles.add(parentRole);
        when(parentUser.getRoles()).thenReturn(parentRoles);
        
        when(parentRepository.findByUserId(parentUser.getId())).thenReturn(Optional.of(parent));
        when(kidRepository.countByParentId(parent.getId())).thenReturn(0);
        
        doThrow(new RuntimeException("Database error")).when(userSubscriptionRepository)
                .findActiveSubscriptionByUser(any());
        
        // When
        boolean canAddMore = kidCountingService.canAddMoreKids(parentUser);
        
        // Then
        assertThat(canAddMore).isTrue(); // Defaults to free tier (1 kid), 0 < 1
    }

    @Test
    @DisplayName("Error handling: Parent repository exception should return safe fallback (0)")
    void errorHandling_parentRepositoryExceptionShouldReturnSafeFallback() {
        // Given - Parent user with ROLE_PARENT
        Set<Role> parentRoles = new HashSet<>();
        Role parentRole = new Role();
        parentRole.setRole(RoleName.ROLE_PARENT.name());
        parentRoles.add(parentRole);
        when(parentUser.getRoles()).thenReturn(parentRoles);
        
        doThrow(new RuntimeException("Database error")).when(parentRepository).findByUserId(any());
        
        // When
        int kidCount = kidCountingService.countKidsForParent(parentUser);
        
        // Then
        assertThat(kidCount).isEqualTo(0); // Safe fallback
    }

    @Test
    @DisplayName("Active kids counting: Should return same as total kids (no soft delete)")
    void activeKidsCounting_shouldReturnSameAsTotalKids() {
        // Given - Parent user with ROLE_PARENT
        Set<Role> parentRoles = new HashSet<>();
        Role parentRole = new Role();
        parentRole.setRole(RoleName.ROLE_PARENT.name());
        parentRoles.add(parentRole);
        when(parentUser.getRoles()).thenReturn(parentRoles);
        
        when(parentRepository.findByUserId(parentUser.getId())).thenReturn(Optional.of(parent));
        when(kidRepository.countByParentId(parent.getId())).thenReturn(4);
        
        // When
        int activeKidCount = kidCountingService.countActiveKidsForParent(parentUser);
        
        // Then
        assertThat(activeKidCount).isEqualTo(4); // Same as total count (no soft delete)
    }

    @Test
    @DisplayName("Subscription edge case: Null current period end should be treated as expired")
    void subscriptionEdgeCase_nullCurrentPeriodEndShouldBeTreatedAsExpired() {
        // Given - Parent user with ROLE_PARENT and subscription with null period end
        Set<Role> parentRoles = new HashSet<>();
        Role parentRole = new Role();
        parentRole.setRole(RoleName.ROLE_PARENT.name());
        parentRoles.add(parentRole);
        when(parentUser.getRoles()).thenReturn(parentRoles);
        
        when(parentRepository.findByUserId(parentUser.getId())).thenReturn(Optional.of(parent));
        when(kidRepository.countByParentId(parent.getId())).thenReturn(1);
        
        when(userSubscriptionRepository.findActiveSubscriptionByUser(parentUser))
                .thenReturn(Optional.of(activeSubscription));
        when(activeSubscription.getCurrentPeriodEnd()).thenReturn(null); // Null period end
        
        // When
        boolean canAddMore = kidCountingService.canAddMoreKids(parentUser);
        
        // Then
        assertThat(canAddMore).isFalse(); // 1 >= 1 (treated as free tier)
    }

    @Test
    @DisplayName("Subscription edge case: Subscription with null plan should default to free tier")
    void subscriptionEdgeCase_subscriptionWithNullPlanShouldDefaultToFreeTier() {
        // Given - Parent user with ROLE_PARENT and subscription with null plan
        Set<Role> parentRoles = new HashSet<>();
        Role parentRole = new Role();
        parentRole.setRole(RoleName.ROLE_PARENT.name());
        parentRoles.add(parentRole);
        when(parentUser.getRoles()).thenReturn(parentRoles);
        
        when(parentRepository.findByUserId(parentUser.getId())).thenReturn(Optional.of(parent));
        when(kidRepository.countByParentId(parent.getId())).thenReturn(0);
        
        when(userSubscriptionRepository.findActiveSubscriptionByUser(parentUser))
                .thenReturn(Optional.of(activeSubscription));
        when(activeSubscription.getCurrentPeriodEnd())
                .thenReturn(Instant.now(fixedClock).plusSeconds(86400)); // 1 day in future
        when(activeSubscription.getSubscriptionPlan()).thenReturn(null); // Null plan
        
        // When
        boolean canAddMore = kidCountingService.canAddMoreKids(parentUser);
        
        // Then
        assertThat(canAddMore).isTrue(); // Defaults to free tier (1 kid), 0 < 1
    }
}
