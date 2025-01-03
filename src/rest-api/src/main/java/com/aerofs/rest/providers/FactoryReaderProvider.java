/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.rest.providers;

import com.aerofs.base.ParamFactory;
import com.google.common.collect.Maps;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.sun.jersey.api.container.ContainerException;
import com.sun.jersey.api.container.MappableContainerException;
import com.sun.jersey.api.core.HttpContext;
import com.sun.jersey.spi.StringReader;
import com.sun.jersey.spi.StringReaderProvider;

import javax.ws.rs.core.Context;
import javax.ws.rs.ext.Provider;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.Map;

/**
 * Reflection-based Jersey black magic to find factory member classes / methods for parameters
 * that do not support straightforward ctors
 *
 * This class supports the following two patterns
 *
 * {@code
 * class Foo
 * {
 *     @ParamFactory
 *     public static Foo create(String s) {}
 * }
 *
 * class Bar
 * {
 *     public static class Factory
 *     {
 *         @Inject
 *         public Factory() {}
 *
 *         @ParamFactory
 *         public Bar create(String s) {}
 *     }
 * }
 * }
 *
 * For added black magic, the factory method can optionally accept the jersey's HttpContext object
 * for the request as its second parameter.
 */
@Provider
public class FactoryReaderProvider implements StringReaderProvider<Object>
{
    private @Context HttpContext _cxt;

    private final Injector _inj;

    private final StringReader<Object> INVALID = new StringReader<Object>() {
        @Override
        public Object fromString(String s) { return null; }
    };

    // cache reader objects by class to avoid the costly reflection associated with their creation
    private final Map<Class<?>, StringReader<Object>> _readers = Maps.newHashMap();

    @Inject
    public FactoryReaderProvider(Injector inj)
    {
        _inj = inj;
    }

    @Override
    public StringReader<Object> getStringReader(Class<?> c, Type type, Annotation[] annotations)
    {
        StringReader<Object> i = _readers.get(c);
        if (i == null) {
            final Class<?> f = factoryClass(c);
            final Method m = f != null
                    ? factoryMethod(c, f)
                    : factoryMethod(c, c);
            i = m == null
                ? INVALID
                : new FactoryReader(f != null ? _inj.getInstance(f) : null, m);
        }
        return i != INVALID ? i : null;
    }

    private class FactoryReader implements StringReader<Object>
    {
        private final Object _o;
        private final Method _m;

        FactoryReader(Object o, Method m)
        {
            _o = o;
            _m = m;
        }

        @Override
        public Object fromString(String s)
        {
            try {
                return _m.getParameterCount() == 1
                        ? _m.invoke(_o, s)
                        : _m.invoke(_o, s, _cxt);
            } catch (InvocationTargetException e) {
                Throwable t = e.getCause();
                // rethrow unchecked exceptions without Mappable wrapping to ensure we get a
                // ParamException wrapping
                if (t instanceof RuntimeException) throw (RuntimeException)t;
                throw new MappableContainerException(t);
            } catch (IllegalAccessException e) {
                throw new ContainerException(e.getCause());
            }
        }
    }

    /**
     * Find a suitable factory member class.
     *
     * Such a class must be static and have a suitable factory method.
     *
     * See {@link #factoryMethod}
     */
    private static Class<?> factoryClass(Class<?> c)
    {
        for (Class<?> cc : c.getDeclaredClasses()) {
            if (Modifier.isStatic(cc.getModifiers()) && factoryMethod(c, cc) != null) {
                return cc;
            }
        }
        return null;
    }

    /**
     * Find a suitable factory method in class {@paramref f} that constructs an object of
     * class {@paramref c} from a String
     *
     * The method must be annotated with @ParamFactory, take a single String argument and
     * have a return type from which an object of class {@paramref c} can be assigned.
     *
     * The method must be static if and only if {@paramref c} and {@paramref f} are equal.
     */
    private static Method factoryMethod(Class<?> c, Class<?> f)
    {
        for (Method m : f.getDeclaredMethods()) {
            int p = m.getParameterCount();
            Class<?>[] pt = m.getParameterTypes();
            if (m.isAnnotationPresent(ParamFactory.class)
                    && c.isAssignableFrom(m.getReturnType())
                    && (p == 1 || (p == 2 && pt[1] == HttpContext.class))
                    && pt[0].isAssignableFrom(String.class)
                    && (Modifier.isStatic(m.getModifiers()) == c.equals(f))) {
                return m;
            }
        }
        return null;
    }
}
