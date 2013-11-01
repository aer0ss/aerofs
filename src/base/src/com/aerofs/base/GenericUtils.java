/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.base;

import com.google.common.base.Preconditions;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

import static com.google.common.base.Preconditions.checkNotNull;

public class GenericUtils
{
    public static <T> Class<T> getTypeClass(Class<?> clazz)
    {
        return getTypeClass(clazz, 0);
    }

    @SuppressWarnings("unchecked")
    public static <T> Class<T> getTypeClass(Class<?> clazz, int idx) {
        Type t = checkNotNull(clazz);
        while (t instanceof Class<?>) t = ((Class<?>) t).getGenericSuperclass();

        if (!(t instanceof ParameterizedType)) throw new IllegalArgumentException();

        Type[] params = ((ParameterizedType) t).getActualTypeArguments();
        Preconditions.checkState(params[idx] instanceof Class<?>);
        return (Class<T>)params[idx];
    }
}
