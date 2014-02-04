/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.base;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotate classes or fields with this to prevent Proguard from obfuscating it
 *
 * This is particularly useful for pacific coexistence with libraries that work
 * their magic using reflection (of which GSON is a prime example)
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE,ElementType.FIELD})
public @interface NoObfuscation
{
}
