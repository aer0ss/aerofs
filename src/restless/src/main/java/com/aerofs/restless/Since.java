package com.aerofs.restless;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Method-level annotation used to implement automatic versioning of JAX-RS resource methods
 *
 * The value MUST be a valid string representation of {@link Version}, i.e.
 * decimal major and minor versions separated by a period.
 *
 * See {@link com.aerofs.restless.jersey.VersionFilterFactory} for implementation details.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface Since
{
    public String value();
}
