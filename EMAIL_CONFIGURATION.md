# Email Service Configuration Guide

This guide explains how to configure the email service for the KidsGPT backend application using environment variables and GitHub secrets.

## Overview

The email service is used for sending password reset emails and other notifications. It supports multiple email providers and can be configured for both local development and production deployment.

## Environment Variables

### Required Email Configuration

| Variable | Description | Default | Example |
|----------|-------------|---------|---------|
| `MAIL_HOST` | SMTP server host | `smtp.gmail.com` | `smtp.gmail.com` |
| `MAIL_PORT` | SMTP server port | `587` | `587` |
| `MAIL_USERNAME` | Email username/address | - | `your-email@gmail.com` |
| `MAIL_PASSWORD` | Email password/app password | - | `your-app-password` |
| `MAIL_FROM` | From email address | `MAIL_USERNAME` | `noreply@yourdomain.com` |
| `MAIL_ENABLED` | Enable/disable email service | `true` | `true` |
| `FRONTEND_URL` | Frontend URL for reset links | `http://localhost:3000` | `https://yourdomain.com` |

## Local Development Setup

### 1. Create .env file

Create a `.env` file in the project root with your email configuration:

```bash
# Email Configuration
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=your-email@gmail.com
MAIL_PASSWORD=your-app-password
MAIL_FROM=your-email@gmail.com
MAIL_ENABLED=true

# Frontend URL
FRONTEND_URL=http://localhost:3000
```

### 2. Gmail Setup (Recommended for Development)

1. Enable 2-Factor Authentication on your Gmail account
2. Generate an App Password:
   - Go to Google Account settings
   - Security → 2-Step Verification → App passwords
   - Generate a password for "Mail"
3. Use the generated password as `MAIL_PASSWORD`

### 3. Other Email Providers

#### Outlook/Hotmail
```bash
MAIL_HOST=smtp-mail.outlook.com
MAIL_PORT=587
```

#### Yahoo
```bash
MAIL_HOST=smtp.mail.yahoo.com
MAIL_PORT=587
```

#### Custom SMTP Server
```bash
MAIL_HOST=your-smtp-server.com
MAIL_PORT=587
```

## Production Deployment

### VPS Deployment with .env file

1. Create a `.env` file on your VPS with production values:
```bash
# Production Email Configuration
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=your-production-email@gmail.com
MAIL_PASSWORD=your-production-app-password
MAIL_FROM=noreply@yourdomain.com
MAIL_ENABLED=true

# Production Frontend URL
FRONTEND_URL=https://yourdomain.com
```

2. Deploy using docker-compose:
```bash
docker-compose up -d
```

### GitHub Actions Deployment with Secrets

1. Add the following secrets to your GitHub repository:
   - `MAIL_HOST`
   - `MAIL_PORT`
   - `MAIL_USERNAME`
   - `MAIL_PASSWORD`
   - `MAIL_FROM`
   - `MAIL_ENABLED`
   - `FRONTEND_URL`

2. The GitHub Actions workflow will automatically create the `.env` file on your VPS using these secrets.

## Security Best Practices

### 1. Use App Passwords
- Never use your main email password
- Generate app-specific passwords for each environment
- Rotate passwords regularly

### 2. Environment-Specific Configuration
- Use different email accounts for development and production
- Use different app passwords for each environment
- Consider using a dedicated email service for production

### 3. Sensitive Data Protection
- Never commit `.env` files to version control
- Use GitHub secrets for production deployment
- Regularly rotate sensitive credentials

## Testing Email Configuration

### 1. Enable Debug Logging

Add to `application.properties`:
```properties
logging.level.org.springframework.mail=DEBUG
logging.level.com.sun.mail=DEBUG
```

### 2. Test Email Service

The application will log email configuration on startup. Check the logs for:
- Email configuration validation
- SMTP connection status
- Email sending attempts

### 3. Disable Email Service

To disable email sending (useful for testing):
```bash
MAIL_ENABLED=false
```

## Troubleshooting

### Common Issues

1. **Authentication Failed**
   - Verify username and password
   - Ensure 2FA is enabled and app password is used
   - Check if account allows "less secure apps"

2. **Connection Timeout**
   - Verify SMTP host and port
   - Check firewall settings
   - Ensure network connectivity

3. **Email Not Received**
   - Check spam folder
   - Verify recipient email address
   - Check email service logs

### Debug Commands

```bash
# Test SMTP connection
telnet smtp.gmail.com 587

# Check application logs
docker-compose logs app

# Verify environment variables
docker-compose exec app env | grep MAIL
```

## Configuration Validation

The application validates email configuration on startup:

- Required fields are present
- Email format is valid
- SMTP settings are reasonable
- Service status is logged

If validation fails, the application will log warnings but continue to start (email service will be disabled).

## Support

For issues with email configuration:
1. Check application logs for error messages
2. Verify environment variables are set correctly
3. Test SMTP connection manually
4. Review email provider documentation 