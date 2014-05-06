/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.base;

import com.google.common.base.Preconditions;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class GenericUtils
{
    public static <T> Class<T> getTypeClass(Class<?> clazz)
    {
        return getTypeClass(clazz, 0);
    }

    public static <T> Class<T> getTypeClass(Class<?> clazz, int idx) {
        Type t = checkNotNull(clazz);
        while (t instanceof Class<?>) t = ((Class<?>) t).getGenericSuperclass();

        if (!(t instanceof ParameterizedType)) throw new IllegalArgumentException();
        return getType(((ParameterizedType) t), idx);
    }

    @SuppressWarnings("unchecked")
    private static <T> Class<T> getType(ParameterizedType t, int idx)
    {
        Type[] params = t.getActualTypeArguments();
        Preconditions.checkState(params[idx] instanceof Class<?>);
        return (Class<T>)params[idx];
    }

    public static <T> Class<T> getTypeInterface(Class<?> c, Class<?> i, int idx)
    {
        checkArgument(i.isInterface());
        for (Type t : c.getGenericInterfaces()) {
            if (t instanceof ParameterizedType) {
                ParameterizedType pt = (ParameterizedType)t;
                if (pt.getRawType().equals(i)) return getType(pt, idx);
            }
        }
        throw new IllegalArgumentException();
    }
}
