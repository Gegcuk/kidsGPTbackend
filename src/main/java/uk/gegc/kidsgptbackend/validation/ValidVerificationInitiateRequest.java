package uk.gegc.kidsgptbackend.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = VerificationInitiateRequestValidator.class)
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidVerificationInitiateRequest {
    String message() default "Invalid verification request: contact info must match verification method";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
} 