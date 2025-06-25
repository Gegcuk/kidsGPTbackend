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
app.rate-limit.auth.requests-per-minute=${AUTH_RATE_LIMIT:5}
app.rate-limit.chat.requests-per-minute=${CHAT_RATE_LIMIT:30}
app.rate-limit.general.requests-per-minute=${GENERAL_RATE_LIMIT:100}
app.rate-limit.register.requests-per-hour=${REGISTER_RATE_LIMIT:3}
```

### Environment Variables

You can override the default values using environment variables:

```bash
export AUTH_RATE_LIMIT=10
export CHAT_RATE_LIMIT=50
export GENERAL_RATE_LIMIT=200
export REGISTER_RATE_LIMIT=5
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

The rate limiting implementation includes comprehensive tests:

- Unit tests for the filter and service
- Integration tests for end-to-end functionality
- Tests for different endpoint types and scenarios

## Security Considerations

1. **JWT Token Validation**: Username extraction from JWT tokens is validated before use
2. **IP Address Handling**: Proper handling of X-Forwarded-For headers for proxy scenarios
3. **Graceful Degradation**: Rate limiting failures don't break the application
4. **Configurable Limits**: Different limits for different endpoint types based on risk

## Performance Impact

- **Memory Usage**: Per-user buckets are stored in memory (ConcurrentHashMap)
- **CPU Impact**: Minimal - Bucket4j is highly optimized
- **Latency**: < 1ms per request for rate limit checking

## Troubleshooting

### Common Issues

1. **Rate limits too strict**: Adjust the configuration values
2. **Memory usage high**: Consider implementing bucket cleanup for inactive users
3. **False positives**: Check if requests are coming from legitimate load balancers

### Debugging

Enable debug logging for rate limiting:

```properties
logging.level.uk.gegc.kidsgptbackend.security.RateLimitFilter=DEBUG
logging.level.uk.gegc.kidsgptbackend.security.RateLimitService=DEBUG
```

## Future Enhancements

Potential improvements for the rate limiting system:

1. **Redis-based buckets**: For distributed deployments
2. **Dynamic rate limiting**: Based on user tier/plan
3. **Rate limit analytics**: Dashboard for monitoring usage patterns
4. **Whitelist functionality**: Exclude specific IPs or users from rate limiting 