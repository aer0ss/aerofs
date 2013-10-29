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
    @SuppressWarnings("unchecked")
    public static <T> Class<T> getTypeClass(Class<?> clazz) {
        Type t = checkNotNull(clazz);
        while (t instanceof Class<?>) t = ((Class<?>) t).getGenericSuperclass();

        if (!(t instanceof ParameterizedType)) throw new IllegalArgumentException();

        Type[] params = ((ParameterizedType) t).getActualTypeArguments();
        Preconditions.checkState(params.length == 1);
        Preconditions.checkState(params[0] instanceof Class<?>);
        return (Class<T>)params[0];
    }
}
