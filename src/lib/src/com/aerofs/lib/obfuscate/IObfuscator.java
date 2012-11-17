/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.lib.obfuscate;

/**
 * Implement to return the plain text and obfuscated forms of the given object
 * type.
 */
interface IObfuscator<T>
{
    String obfuscate(T object);
    String plainText(T object);
}
