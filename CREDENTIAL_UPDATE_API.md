# Credential Update API Documentation

## Overview

The KidsGPT backend now supports updating user credentials (email and password) for both parent and kid accounts. This functionality allows authenticated users to change their email address and password while maintaining security through proper validation.

## Security Features

- **Authentication Required**: All endpoints require valid JWT authentication
- **Current Password Verification**: Password changes require verification of current password
- **Email Uniqueness**: Email updates are validated to ensure uniqueness
- **Password Strength**: New passwords must meet minimum length requirements
- **Same Password Prevention**: Users cannot set the same password they currently have

## API Endpoints

### Update Email

**PUT** `/api/v1/auth/update-email`

Updates the authenticated user's email address.

**Authentication:** Required (JWT Bearer Token)

**Request Body:** `UpdateEmailRequest`
```json
{
  "newEmail": "newemail@example.com"
}
```

**Validation Rules:**
- `newEmail`: Required, valid email format
- Email must not be already in use by another user

**Response:** `UserProfileDto` (200 OK)
```json
{
  "id": "uuid",
  "username": "string",
  "email": "newemail@example.com",
  "role": "ROLE_PARENT",
  "createdAt": "2024-01-01T12:00:00"
}
```

**Error Responses:**
- `400 Bad Request`: Invalid email format
- `400 Bad Request`: Email already in use
- `401 Unauthorized`: Missing or invalid authentication
- `404 Not Found`: User not found

---

### Update Password

**PUT** `/api/v1/auth/update-password`

Updates the authenticated user's password.

**Authentication:** Required (JWT Bearer Token)

**Request Body:** `UpdatePasswordRequest`
```json
{
  "currentPassword": "oldpassword123",
  "newPassword": "newpassword123"
}
```

**Validation Rules:**
- `currentPassword`: Required, must match current password
- `newPassword`: Required, minimum 8 characters
- New password must be different from current password

**Response:** `UserProfileDto` (200 OK)
```json
{
  "id": "uuid",
  "username": "string",
  "email": "user@example.com",
  "role": "ROLE_PARENT",
  "createdAt": "2024-01-01T12:00:00"
}
```

**Error Responses:**
- `400 Bad Request`: Current password is incorrect
- `400 Bad Request`: New password must be different from current password
- `400 Bad Request`: New password too short (less than 8 characters)
- `401 Unauthorized`: Missing or invalid authentication
- `404 Not Found`: User not found

---

## Data Transfer Objects

### UpdateEmailRequest
```java
public record UpdateEmailRequest(
    @NotBlank(message = "New email must not be blank")
    @Email(message = "New email must be a valid address")
    String newEmail
) {}
```

### UpdatePasswordRequest
```java
public record UpdatePasswordRequest(
    @NotBlank(message = "Current password must not be blank")
    String currentPassword,
    
    @NotBlank(message = "New password must not be blank")
    @Size(min = 8, max = 100, message = "New password length must be at least 8 characters")
    String newPassword
) {}
```

## Error Handling

The API uses custom exceptions for credential-related errors:

### CredentialUpdateException
Thrown when credential update operations fail:
- Email already in use
- Current password is incorrect
- New password is the same as current password

**Response Format:**
```json
{
  "timestamp": "2024-01-01T12:00:00",
  "status": 400,
  "error": "Credential Update Failed",
  "details": ["Email already in use"]
}
```

## Security Considerations

1. **Password Hashing**: All passwords are hashed using BCrypt before storage
2. **JWT Authentication**: All endpoints require valid JWT tokens
3. **Input Validation**: All inputs are validated using Bean Validation
4. **Email Uniqueness**: Email addresses are checked for uniqueness across all users
5. **Password Verification**: Current password is verified before allowing changes

## Usage Examples

### Update Email (JavaScript)
```javascript
const response = await fetch('/api/v1/auth/update-email', {
  method: 'PUT',
  headers: {
    'Content-Type': 'application/json',
    'Authorization': `Bearer ${token}`
  },
  body: JSON.stringify({
    newEmail: 'newemail@example.com'
  })
});

if (response.ok) {
  const userProfile = await response.json();
  console.log('Email updated:', userProfile.email);
}
```

### Update Password (JavaScript)
```javascript
const response = await fetch('/api/v1/auth/update-password', {
  method: 'PUT',
  headers: {
    'Content-Type': 'application/json',
    'Authorization': `Bearer ${token}`
  },
  body: JSON.stringify({
    currentPassword: 'oldpassword123',
    newPassword: 'newpassword123'
  })
});

if (response.ok) {
  const userProfile = await response.json();
  console.log('Password updated successfully');
}
```

## Testing

The functionality is thoroughly tested with integration tests covering:
- Valid credential updates
- Invalid email formats
- Duplicate email addresses
- Incorrect current passwords
- Same password attempts
- Missing authentication
- Validation errors

Run tests with:
```bash
mvn test -Dtest=AuthControllerUpdateCredentialsIntegrationTest
``` 