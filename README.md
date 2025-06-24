# KidsGPT Backend

A Spring Boot backend application for KidsGPT, providing AI-powered chat functionality for children with secure
authentication and password reset capabilities.

## Features

- **User Authentication**: JWT-based authentication with registration and login
- **Password Reset**: Secure email-based password reset functionality
- **AI Chat**: OpenAI-powered chat with content moderation
- **User Management**: User profiles and role-based access control
- **Security**: Comprehensive security measures and input validation

## Quick Start

1. **Clone the repository**
2. **Configure environment variables** (see [Email Configuration Guide](EMAIL_CONFIGURATION.md))
3. **Set up email configuration** (see [Email Configuration Guide](EMAIL_CONFIGURATION.md))
4. **Run the application**: `./mvnw spring-boot:run`
5. **Access the API**: `http://localhost:8080`

## Environment Setup

### Required Environment Variables

Create a `.env` file in the project root:

```bash
# Database Configuration
DB_URL=jdbc:mysql://localhost:3306/kidsgptdb?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
DB_USERNAME=bestuser
DB_PASSWORD=bestuser

# JWT Configuration
JWT_SECRET=your-base64-encoded-256-bit-secret

# OpenAI Configuration
OPENAI_API_KEY=your-openai-api-key

# Email Configuration (see EMAIL_CONFIGURATION.md for details)
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=your-email@gmail.com
MAIL_PASSWORD=your-app-password
MAIL_FROM=your-email@gmail.com
MAIL_ENABLED=true

# Frontend URL
FRONTEND_URL=http://localhost:3000
```

### Email Configuration

For detailed email setup instructions, see the [Email Configuration Guide](EMAIL_CONFIGURATION.md).

**Quick Gmail Setup:**
1. Enable 2-Factor Authentication on your Gmail account
2. Generate an App Password (Google Account → Security → App passwords)
3. Use the generated password as `MAIL_PASSWORD`

## Docker Deployment

### Local Development
```bash
docker-compose up -d
```

### Production Deployment
1. Set up GitHub secrets (see [Email Configuration Guide](EMAIL_CONFIGURATION.md))
2. Push to main branch to trigger automatic deployment
3. Or manually deploy using the provided GitHub Actions workflow

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

## API Documentation

Access Swagger UI at: `http://localhost:8080/swagger-ui/`

## Configuration Files

- `env.template` - Template for environment variables
- `EMAIL_CONFIGURATION.md` - Detailed email setup guide
- `docker-compose.yml` - Docker deployment configuration
- `.github/workflows/deploy.yml` - GitHub Actions deployment workflow

## License

[Add your license information here]
