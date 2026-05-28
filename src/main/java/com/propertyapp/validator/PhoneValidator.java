package com.propertyapp.validator;

import com.propertyapp.util.ValidationUtils;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validator for phone numbers
 */
public class PhoneValidator implements ConstraintValidator<ValidPhone, String> {
    
    @Override
    public void initialize(ValidPhone constraintAnnotation) {
        // No initialization needed
    }
    
    @Override
    public boolean isValid(String phone, ConstraintValidatorContext context) {
        if (phone == null || phone.trim().isEmpty()) {
            return true; // null/empty is valid, use @NotNull if required
        }
        
        return ValidationUtils.isValidPhone(phone);
    }
}