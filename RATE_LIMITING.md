# Rate Limiting Configuration

This document describes the rate limiting implementation in the KidsGPT backend application.

## Overview

The application implements rate limiting using Bucket4j to protect against abuse and ensure fair usage of resources. Rate limits are applied at the filter level before authentication, providing protection against brute force attacks and API abuse.

## Rate Limit Categories

### 1. Authentication Endpoints (`/api/v1/auth/*`)
- **Default**: 5 requests per minute
- **Purpose**: Prevent brute force attacks on login/register endpoints
- **Scope**: Global (applies to all users/IPs)

### 2. Chat Endpoints (`/api/v1/chat`)
- **Default**: 30 requests per minute
- **Purpose**: Prevent abuse of AI chat functionality
- **Scope**: Per-user for authenticated users, global for anonymous users

### 3. Registration Endpoint (`/api/v1/auth/register`)
- **Default**: 3 requests per hour
- **Purpose**: Prevent mass account creation
- **Scope**: Global (applies to all users/IPs)

### 4. General Endpoints (all other API endpoints)
- **Default**: 100 requests per minute
- **Purpose**: General API protection
- **Scope**: Per-user for authenticated users, global for anonymous users

## Configuration

Rate limits can be configured using environment variables or application properties:

```properties
# Rate limiting configuration
app.rate-limit.enabled=${RATE_LIMIT_ENABLED:true}
app.rate-limit.auth.requests-per-minute=${AUTH_RATE_LIMIT:5}
app.rate-limit.chat.requests-per-minute=${CHAT_RATE_LIMIT:30}
app.rate-limit.general.requests-per-minute=${GENERAL_RATE_LIMIT:100}
app.rate-limit.register.requests-per-hour=${REGISTER_RATE_LIMIT:3}
```

### Environment Variables

You can override the default values using environment variables:

```bash
export RATE_LIMIT_ENABLED=true
export AUTH_RATE_LIMIT=10
export CHAT_RATE_LIMIT=50
export GENERAL_RATE_LIMIT=200
export REGISTER_RATE_LIMIT=5
```

### Disabling Rate Limiting

Rate limiting can be completely disabled by setting:

```properties
app.rate-limit.enabled=false
```

This is useful for:
- **Development environments** where rate limiting might interfere with testing
- **Load testing** scenarios
- **Debugging** authentication or API issues

## Test Configuration

### Default Test Behavior

Rate limiting is **disabled by default** in test environments to prevent interference with existing test suites:

```properties
# In application-test.properties
app.rate-limit.enabled=false
```

### Rate Limiting Tests

Specific rate limiting tests use the `ratelimit` profile:

```properties
# In application-ratelimit.properties
app.rate-limit.enabled=true
app.rate-limit.auth.requests-per-minute=5
app.rate-limit.chat.requests-per-minute=10
```

To run rate limiting tests:

```java
@ActiveProfiles("ratelimit")
public class RateLimitIntegrationTest {
    // Tests that verify rate limiting behavior
}
```

## Response Headers

When rate limiting is applied, the following headers are included in responses:

- `X-Rate-Limit-Remaining`: Number of remaining requests allowed
- `X-Rate-Limit-Retry-After-Seconds`: Time to wait before retrying (only when rate limit exceeded)

## Rate Limit Exceeded Response

When a rate limit is exceeded, the application returns:

- **Status Code**: 429 (Too Many Requests)
- **Headers**: 
  - `X-Rate-Limit-Retry-After-Seconds`: Time to wait
- **Body**: "Rate limit exceeded. Try again in X seconds."

## Excluded Endpoints

The following endpoints are excluded from rate limiting:

- `/actuator/*` - Health checks and monitoring
- `/v3/api-docs/*` - API documentation
- `/swagger-ui/*` - Swagger UI
- `/api/v1/health` - Health check endpoint
- `/api/v1/system/status` - System status endpoint

## Implementation Details

### Components

1. **RateLimitConfig**: Defines rate limit buckets with configurable limits
2. **RateLimitService**: Manages rate limit checking and per-user buckets
3. **RateLimitFilter**: HTTP filter that applies rate limiting to requests

### Per-User Rate Limiting

For authenticated users, rate limits are applied per-user using JWT token extraction:

- Chat endpoints: Individual rate limits per user
- General endpoints: Individual rate limits per user
- Authentication endpoints: Global rate limits (for security)

### Anonymous Users

For unauthenticated requests, global rate limits are applied based on the endpoint type.

## Monitoring and Logging

Rate limit violations are logged with the following information:

- Request method and URI
- Client IP address
- Rate limit type that was exceeded

Example log entry:
```
WARN Rate limit exceeded for POST /api/v1/auth/login from IP: 192.168.1.100
```

## Testing

### Test Environment Configuration

Rate limiting is disabled by default in tests to prevent interference:

```properties
# application-test.properties
app.rate-limit.enabled=false
```

### Rate Limiting Specific Tests

Use the `ratelimit` profile for tests that need to verify rate limiting behavior:

```java
@SpringBootTest
@ActiveProfiles("ratelimit")
public class RateLimitIntegrationTest {
    // Rate limiting tests
}
```

### Running Tests

```bash
# Run all tests (rate limiting disabled)
mvn test

# Run specific rate limiting tests
mvn test -Dtest=RateLimitIntegrationTest

# Run with rate limiting enabled
mvn test -Dspring.profiles.active=ratelimit
```

## Security Considerations

1. **JWT Token Validation**: Username extraction from JWT tokens is validated before use
2. **IP Address Handling**: Proper handling of X-Forwarded-For headers for proxy scenarios
3. **Graceful Degradation**: Rate limiting failures don't break the application
4. **Configurable Limits**: Different limits for different endpoint types based on risk
5. **Test Isolation**: Rate limiting disabled in tests to prevent false failures

## Performance Impact

- **Memory Usage**: Per-user buckets are stored in memory (ConcurrentHashMap)
- **CPU Impact**: Minimal - Bucket4j is highly optimized
- **Latency**: < 1ms per request for rate limit checking
- **Test Performance**: No impact when disabled in test environments

## Troubleshooting

### Common Issues

1. **Rate limits too strict**: Adjust the configuration values
2. **Memory usage high**: Consider implementing bucket cleanup for inactive users
3. **False positives**: Check if requests are coming from legitimate load balancers
4. **Tests failing**: Ensure `app.rate-limit.enabled=false` in test configuration

### Debugging

Enable debug logging for rate limiting:

```properties
logging.level.uk.gegc.kidsgptbackend.security.RateLimitFilter=DEBUG
logging.level.uk.gegc.kidsgptbackend.security.RateLimitService=DEBUG
```

### Test Debugging

If existing tests start failing after adding rate limiting:

1. Check that `app.rate-limit.enabled=false` in `application-test.properties`
2. Verify test profile configuration
3. Use `@ActiveProfiles("test")` in test classes
4. For rate limiting tests, use `@ActiveProfiles("ratelimit")`

## Future Enhancements

Potential improvements for the rate limiting system:

1. **Redis-based buckets**: For distributed deployments
2. **Dynamic rate limiting**: Based on user tier/plan
3. **Rate limit analytics**: Dashboard for monitoring usage patterns
4. **Whitelist functionality**: Exclude specific IPs or users from rate limiting
5. **Automatic bucket cleanup**: Remove inactive user buckets to save memory 