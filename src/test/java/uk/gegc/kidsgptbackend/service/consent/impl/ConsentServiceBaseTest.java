package uk.gegc.kidsgptbackend.service.consent.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import uk.gegc.kidsgptbackend.dto.consent.ConsentGrantRequest;
import uk.gegc.kidsgptbackend.model.consent.ConsentSource;
import uk.gegc.kidsgptbackend.model.consent.ConsentType;
import uk.gegc.kidsgptbackend.model.consent.LawfulBasis;
import uk.gegc.kidsgptbackend.repository.consent.ConsentChildCoverageRepository;
import uk.gegc.kidsgptbackend.repository.consent.ConsentLedgerRepository;
import uk.gegc.kidsgptbackend.repository.consent.ConsentPoliciesRepository;
import uk.gegc.kidsgptbackend.repository.consent.ParentVerificationRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
abstract class ConsentServiceBaseTest {

    @Mock
    protected ConsentLedgerRepository consentLedgerRepository;

    @Mock
    protected ConsentChildCoverageRepository consentChildCoverageRepository;

    @Mock
    protected ParentVerificationRepository parentVerificationRepository;
    
    @Mock
    protected ConsentPoliciesRepository consentPoliciesRepository;

    @InjectMocks
    protected ConsentServiceImpl consentService;

    protected ConsentGrantRequest validRequest;
    protected UUID testUserId;
    protected UUID testVerificationId;
    protected List<UUID> testKids;
    protected String serverIp;
    protected String serverUa;

    @BeforeEach
    void setUp() {
        // Set up configuration values
        ReflectionTestUtils.setField(consentService, "hmacSecret", "test-hmac-secret-key");
        ReflectionTestUtils.setField(consentService, "defaultRetentionYears", 7);
        ReflectionTestUtils.setField(consentService, "clock", java.time.Clock.systemUTC());

        // Create test data
        testUserId = UUID.randomUUID();
        testVerificationId = UUID.randomUUID();
        testKids = List.of(UUID.randomUUID(), UUID.randomUUID());

        // Default server captured values
        serverIp = "203.0.113.5";
        serverUa = "JUnitAgent/1.0";

        // Setup common mocks
        lenient().when(parentVerificationRepository.findById(any(UUID.class))).thenReturn(Optional.empty());

        MockHttpServletRequest mockRequest = new MockHttpServletRequest();
        mockRequest.setAttribute("requestContext", new uk.gegc.kidsgptbackend.util.RequestContext(serverIp, serverUa));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(mockRequest));

        validRequest = new ConsentGrantRequest(
                testUserId,
                ConsentType.PRIVACY_POLICY,
                "1.0.0",
                "https://example.com/privacy",
                "abc123hash",
                testVerificationId,
                "GB",
                "England",
                "en-GB",
                ConsentSource.WEB,
                testKids,
                "192.168.1.1",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
                LawfulBasis.CONSENT
        );
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }
} 