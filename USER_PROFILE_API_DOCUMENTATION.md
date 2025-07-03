# KidsGPT User/Profile Management API Documentation

## Overview

The KidsGPT user/profile management system provides secure authentication and profile management for two user types:
- **Parents**: Can register themselves and create kid accounts
- **Kids**: Can only be created by parents, have privacy-focused profiles with minimal personal data

## Security Model

- **JWT Authentication**: Bearer tokens required for protected endpoints
- **Role-Based Access Control**:
  - `ROLE_PARENT`: Can create kids, update kid profiles
  - `ROLE_CHILD`: Can only update own avatar
  - `ROLE_ADMIN`: Full system access

## API Endpoints

### Authentication Endpoints

#### 1. Parent Registration
**POST** `/api/v1/auth/register`

Creates a new parent account with `ROLE_PARENT` role.

**Request Body:** `RegisterUserRequest`
```json
{
  "username": "string",
  "email": "string",
  "password": "string"
}
```

**Validation Rules:**
- `username`: Required, 4-20 characters
- `email`: Required, valid email format
- `password`: Required, minimum 8 characters

**Response:** `UserDto` (201 Created)
```json
{
  "id": "uuid",
  "username": "string",
  "email": "string",
  "isActive": true,
  "roles": ["ROLE_PARENT"],
  "createdAt": "2024-01-01T12:00:00",
  "lastLoginDate": null,
  "updatedAt": null
}
```

**Error Responses:**
- `400 Bad Request`: Validation errors
- `409 Conflict`: Username or email already exists

---

#### 2. Kid Registration (Parent-Only)
**POST** `/api/v1/auth/register-kid`

Creates a new kid account. Only authenticated parents can create kids.

**Authentication:** Required (`ROLE_PARENT`)

**Request Body:** `KidRegistrationRequest`
```json
{
  "nickname": "string",
  "password": "string",
  "ageGroup": "AGE_6_8" | "AGE_9_10" | "AGE_11_12" | "AGE_13_14" | "AGE_15_16"
}
```

**Validation Rules:**
- `nickname`: Required, 2-50 characters
- `password`: Required, 6-100 characters
- `ageGroup`: Required, valid AgeGroup enum

**Response:** `KidDto` (201 Created)
```json
{
  "id": "uuid",
  "nickname": "string",
  "username": "generated_username",
  "ageGroup": "AGE_6_8",
  "favoriteColor": null,
  "avatarId": null,
  "interests": null
}
```

**Username Generation:**
- Format: `{nickname}_kid`, `{nickname}_kid1`, etc.
- Automatic collision handling ensures uniqueness

**Error Responses:**
- `400 Bad Request`: Validation errors
- `401 Unauthorized`: Not authenticated
- `403 Forbidden`: Not a parent user

---

#### 3. Login (Parents & Kids)
**POST** `/api/v1/auth/login`

Authenticates users and returns JWT tokens.

**Request Body:** `AuthLoginRequest`
```json
{
  "usernameOrEmail": "string",
  "password": "string"
}
```

**Validation Rules:**
- `usernameOrEmail`: Required, accepts username or email
- `password`: Required

**Response:** `AuthTokensResponse` (200 OK)
```json
{
  "accessToken": "jwt_token",
  "refreshToken": "jwt_refresh_token",
  "accessExpiresInMs": 3600000,
  "refreshExpiresInMs": 86400000
}
```

**Error Responses:**
- `400 Bad Request`: Validation errors
- `401 Unauthorized`: Invalid credentials

---

#### 4. Logout
**POST** `/api/v1/auth/logout`

Invalidates the current JWT token.

**Headers:** `Authorization: Bearer {token}` (optional)

**Response:** `204 No Content`

---

#### 5. Get Current User Profile
**GET** `/api/v1/auth/me`

Returns the authenticated user's profile information.

**Authentication:** Required

**Response:** `UserProfileDto` (200 OK)
```json
{
  "id": "uuid",
  "username": "string",
  "email": "string",
  "role": "ROLE_PARENT" | "ROLE_CHILD" | "ROLE_ADMIN",
  "createdAt": "2024-01-01T12:00:00"
}
```

**Error Responses:**
- `401 Unauthorized`: Not authenticated

---

### Password Reset Endpoints

#### 6. Forgot Password
**POST** `/api/v1/auth/forgot-password`

Initiates password reset process by sending reset email.

**Request Body:** `ForgotPasswordRequest`
```json
{
  "email": "string"
}
```

**Response:** `PasswordResetResponse` (200 OK)
```json
{
  "message": "Password reset email sent"
}
```

---

#### 7. Reset Password
**POST** `/api/v1/auth/reset-password`

Resets password using the token from email.

**Request Body:** `ResetPasswordRequest`
```json
{
  "token": "string",
  "newPassword": "string"
}
```

**Response:** `204 No Content`

---

#### 8. Validate Reset Token
**GET** `/api/v1/auth/validate-reset-token?token={token}`

Validates if a password reset token is still valid.

**Response:** `boolean` (200 OK)

---

### Profile Management Endpoints

#### 9. Kid Self-Update (Avatar Only)
**PATCH** `/api/v1/profile`

Allows kids to update their own avatar. This is the only field kids can modify.

**Authentication:** Required (`ROLE_CHILD`)

**Request Body:** `KidSelfUpdateRequest`
```json
{
  "avatarId": "string"
}
```

**Validation Rules:**
- `avatarId`: Optional string

**Response:** `ChildProfileDto` (200 OK)
```json
{
  "id": "uuid",
  "name": "nickname",
  "age": 8,
  "interests": "existing_interests",
  "avatarId": "updated_avatar_id",
  "ageGroup": "AGE_6_8"
}
```

**Error Responses:**
- `401 Unauthorized`: Not authenticated
- `403 Forbidden`: Not a child user

---

#### 10. Parent Update Kid Profile
**PATCH** `/api/v1/profile/kid/{kidId}`

Allows parents to update their kids' profiles (nickname, password, age group).

**Authentication:** Required (`ROLE_PARENT`)

**Path Parameters:**
- `kidId`: UUID of the kid to update

**Request Body:** `ParentUpdateKidRequest`
```json
{
  "nickname": "string",
  "password": "string",
  "ageGroup": "AGE_6_8" | "AGE_9_10" | "AGE_11_12" | "AGE_13_14" | "AGE_15_16"
}
```

**Validation Rules:**
- `nickname`: Required, 2-50 characters
- `password`: Optional, 6-100 characters (only updates if provided)
- `ageGroup`: Required, valid AgeGroup enum

**Response:** `ChildProfileDto` (200 OK)
```json
{
  "id": "uuid",
  "name": "updated_nickname",
  "age": 10,
  "interests": "existing_interests",
  "avatarId": "existing_avatar",
  "ageGroup": "AGE_9_10"
}
```

**Error Responses:**
- `400 Bad Request`: Validation errors
- `401 Unauthorized`: Not authenticated
- `403 Forbidden`: Not a parent user
- `404 Not Found`: Kid not found or not owned by parent

---

## Data Models

### Age Groups
The system supports the following age groups:
- `AGE_6_8`: Ages 6-8
- `AGE_9_10`: Ages 9-10
- `AGE_11_12`: Ages 11-12
- `AGE_13_14`: Ages 13-14
- `AGE_15_16`: Ages 15-16

### User Roles
- `ROLE_PARENT`: Can register, create kids, update kid profiles
- `ROLE_CHILD`: Can login, update own avatar only
- `ROLE_ADMIN`: Full system access

### Privacy & Security Features

1. **Kid Privacy Protection:**
   - No real names stored (only nicknames)
   - No birthdate storage
   - Generated usernames for anonymity
   - Minimal profile data

2. **Access Control:**
   - Kids cannot create other accounts
   - Parents can only modify their own kids
   - Role-based endpoint protection

3. **Data Validation:**
   - Comprehensive input validation
   - Age-appropriate constraints
   - Secure password requirements

## API Usage Examples

### Complete Parent-Kid Flow

```bash
# 1. Register as parent
curl -X POST /api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "parent123",
    "email": "parent@example.com",
    "password": "securepass123"
  }'

# 2. Login as parent
curl -X POST /api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "usernameOrEmail": "parent123",
    "password": "securepass123"
  }'

# 3. Create kid account (using parent token)
curl -X POST /api/v1/auth/register-kid \
  -H "Authorization: Bearer {parent_token}" \
  -H "Content-Type: application/json" \
  -d '{
    "nickname": "Emma",
    "password": "kidpass123",
    "ageGroup": "AGE_6_8"
  }'

# 4. Login as kid
curl -X POST /api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "usernameOrEmail": "emma_kid",
    "password": "kidpass123"
  }'

# 5. Kid updates avatar
curl -X PATCH /api/v1/profile \
  -H "Authorization: Bearer {kid_token}" \
  -H "Content-Type: application/json" \
  -d '{
    "avatarId": "princess_avatar"
  }'

# 6. Parent updates kid's profile
curl -X PATCH /api/v1/profile/kid/{kid_id} \
  -H "Authorization: Bearer {parent_token}" \
  -H "Content-Type: application/json" \
  -d '{
    "nickname": "Emma Rose",
    "ageGroup": "AGE_9_10"
  }'
```

## Error Handling

All endpoints return standardized error responses:

```json
{
  "timestamp": "2024-01-01T12:00:00.000Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/v1/auth/register"
}
```

Common HTTP status codes:
- `200 OK`: Success
- `201 Created`: Resource created
- `204 No Content`: Success with no response body
- `400 Bad Request`: Validation errors
- `401 Unauthorized`: Authentication required
- `403 Forbidden`: Insufficient permissions
- `404 Not Found`: Resource not found
- `409 Conflict`: Resource already exists 