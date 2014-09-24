package com.aerofs.polaris.ids;

import javax.validation.Constraint;
import javax.validation.Payload;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target({FIELD, PARAMETER})
@Retention(RUNTIME)
@Constraint(validatedBy = IdentifierValidator.class)
public @interface Identifier {

    String message() default "{com.aerofs.polaris.ids.Identifier.message}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
