package com.aerofs.polaris.ids;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

public final class IdentifierValidator implements ConstraintValidator<Identifier, String>{

    private static final Logger LOGGER = LoggerFactory.getLogger(IdentifierValidator.class);

    @Override
    public void initialize(Identifier constraintAnnotation) {
        // noop
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        try {
            return Identifiers.hasValdiIdentifierLength(Identifiers.hexDecode(value));
        } catch (IllegalArgumentException e) {
            LOGGER.warn("fail convert {} to identifier", value);
            return false;
        }
    }
}
