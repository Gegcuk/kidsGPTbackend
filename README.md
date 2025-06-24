# KidsGPT Backend

A Spring Boot backend application for KidsGPT, providing AI-powered chat functionality for children with secure authentication and password reset capabilities.

## Features

- **User Authentication**: JWT-based authentication with registration and login
- **Password Reset**: Secure email-based password reset functionality
- **AI Chat**: OpenAI-powered chat with content moderation
- **User Management**: User profiles and role-based access control
- **Security**: Comprehensive security measures and input validation

## Password Reset Functionality

The application includes a complete password reset system with the following features:

### Security Features
- **Secure Token Generation**: Uses cryptographically secure random tokens
- **Time-Limited Tokens**: Tokens expire after 1 hour
- **Single-Use Tokens**: Tokens are invalidated after use
- **Email Privacy**: Same response message for existing/non-existing emails (prevents email enumeration)
- **Token Cleanup**: Automatic cleanup of expired and used tokens

### API Endpoints

#### 1. Initiate Password Reset
```http
POST /api/v1/auth/forgot-password
Content-Type: application/json

{
  "email": "user@example.com"
}
```

**Response:**
```json
{
  "message": "If an account with this email exists, a password reset link has been sent.",
  "expiresAt": "2024-01-15T14:30:00"
}
```

#### 2. Reset Password
```http
POST /api/v1/auth/reset-password
Content-Type: application/json

{
  "token": "reset-token-from-email",
  "newPassword": "newSecurePassword123"
}
```

**Response:** `200 OK` (no body)

#### 3. Validate Reset Token
```http
GET /api/v1/auth/reset-password/validate?token=reset-token
```

**Response:** `true` or `false`

### Email Configuration

Configure your email settings in `application.properties`:

```properties
# Email Configuration
spring.mail.host=smtp.gmail.com
spring.mail.port=587
spring.mail.username=${MAIL_USERNAME}
spring.mail.password=${MAIL_PASSWORD}
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.enable=true
spring.mail.properties.mail.smtp.starttls.required=true

# Frontend URL for reset links
app.frontend.url=${FRONTEND_URL:http://localhost:3000}
```

### Environment Variables

Set the following environment variables:

```bash
# Email Configuration
MAIL_USERNAME=your-email@gmail.com
MAIL_PASSWORD=your-app-password
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587

# Frontend URL
FRONTEND_URL=https://your-frontend-domain.com

# JWT Secret (required)
JWT_SECRET=your-base64-encoded-256-bit-secret
```

### Database Schema

The password reset functionality creates a `password_reset_tokens` table:

```sql
CREATE TABLE password_reset_tokens (
    token_id UUID PRIMARY KEY,
    token VARCHAR(255) UNIQUE NOT NULL,
    user_id UUID NOT NULL,
    email VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    used BOOLEAN DEFAULT FALSE,
    used_at TIMESTAMP
);
```

### Security Considerations

1. **Token Security**: Tokens are 256-bit cryptographically secure random values
2. **Expiration**: All tokens expire after 1 hour
3. **Single Use**: Tokens are invalidated immediately after use
4. **Email Privacy**: The system doesn't reveal whether an email exists
5. **Rate Limiting**: Consider implementing rate limiting for production
6. **HTTPS**: Always use HTTPS in production for secure token transmission

### Testing

The application includes comprehensive tests for the password reset functionality:

- **Integration Tests**: `PasswordResetControllerIntegrationTest`
- **Unit Tests**: `PasswordResetServiceImplTest`
- **Email Tests**: Configured with fake SMTP for testing

Run tests with:
```bash
./mvnw test
```

### Frontend Integration

The frontend should:

1. **Request Reset**: Call `/api/v1/auth/forgot-password` with user's email
2. **Display Message**: Show the response message to the user
3. **Handle Reset Link**: Extract token from URL and call `/api/v1/auth/reset-password`
4. **Validate Token**: Optionally call `/api/v1/auth/reset-password/validate` to check token validity

### Example Frontend Flow

```javascript
// 1. User requests password reset
const response = await fetch('/api/v1/auth/forgot-password', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ email: 'user@example.com' })
});

// 2. User clicks link in email (frontend handles this)
// URL: https://your-app.com/reset-password?token=abc123...

// 3. Frontend validates token
const isValid = await fetch(`/api/v1/auth/reset-password/validate?token=${token}`);

// 4. User submits new password
await fetch('/api/v1/auth/reset-password', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ 
    token: 'abc123...', 
    newPassword: 'newSecurePassword123' 
  })
});
```

## Getting Started

1. **Clone the repository**
2. **Configure environment variables**
3. **Set up email configuration**
4. **Run the application**: `./mvnw spring-boot:run`
5. **Access the API**: `http://localhost:8080`

## API Documentation

Access Swagger UI at: `http://localhost:8080/swagger-ui/`

## License

[Add your license information here]
