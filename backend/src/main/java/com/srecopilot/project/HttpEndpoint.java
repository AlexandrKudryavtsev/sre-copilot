package com.srecopilot.project;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = HttpEndpointValidator.class)
public @interface HttpEndpoint {

    String message() default "must be an absolute HTTP or HTTPS URL without credentials or a fragment";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
