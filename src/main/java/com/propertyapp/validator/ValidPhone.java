package com.propertyapp.validator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PhoneValidator.class)
@Documented
public @interface ValidPhone {
    
    String message() default "Invalid phone number format";
    
    Class<?>[] groups() default {};
    
    Class<? extends Payload>[] payload() default {};
}