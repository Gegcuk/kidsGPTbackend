package uk.gegc.kidsgptbackend.shared.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.util.StringUtils;
import uk.gegc.kidsgptbackend.features.consent.api.dto.VerificationInitiateRequest;
import uk.gegc.kidsgptbackend.features.consent.domain.model.VerificationMethod;

import java.util.regex.Pattern;

public class VerificationInitiateRequestValidator implements ConstraintValidator<ValidVerificationInitiateRequest, VerificationInitiateRequest> {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+[1-9]\\d{1,14}$"); // E.164 format

    @Override
    public boolean isValid(VerificationInitiateRequest request, ConstraintValidatorContext context) {
        if (request == null) return true; // let bean validation handle nulls
        if (request.verificationMethod() == null || !StringUtils.hasText(request.contactInfo())) return true;

        context.disableDefaultConstraintViolation();
        String contact = request.contactInfo().trim();

        switch (request.verificationMethod()) {
            case EMAIL -> {
                if (!isValidEmail(contact)) {
                    context.buildConstraintViolationWithTemplate(
                            "contactInfo must be a valid email address when verificationMethod=EMAIL")
                            .addPropertyNode("contactInfo")
                            .addConstraintViolation();
                    return false;
                }
                return true;
            }
            case SMS -> {
                if (!isValidPhone(contact)) {
                    context.buildConstraintViolationWithTemplate(
                            "contactInfo must be an E.164 phone (e.g. +15551234567) when verificationMethod=SMS")
                            .addPropertyNode("contactInfo")
                            .addConstraintViolation();
                    return false;
                }
                return true;
            }
            default -> {
                context.buildConstraintViolationWithTemplate("Unsupported verification method")
                        .addPropertyNode("verificationMethod")
                        .addConstraintViolation();
                return false;
            }
        }
    }

    private boolean isValidEmail(String email) {
        return EMAIL_PATTERN.matcher(email).matches();
    }

    private boolean isValidPhone(String phone) {
        return PHONE_PATTERN.matcher(phone).matches();
    }
} 