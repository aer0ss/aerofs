/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.bifrost.server;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface Transactional
{
    boolean readOnly() default false;
}
