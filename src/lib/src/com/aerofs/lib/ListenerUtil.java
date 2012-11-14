/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.lib;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Set;

public abstract class ListenerUtil
{
    private ListenerUtil()
    {
        // private to enforce uninstantiability
    }

    public static <T> void addListener_(Set<T> ls, T l)
    {
        if (Param.STRICT_LISTENERS) Util.verify(ls.add(l));
        else ls.add(l);
    }

    public static <T> void removeListener_(Set<T> ls, T l)
    {
        if (Param.STRICT_LISTENERS) Util.verify(ls.remove(l));
        else ls.remove(l);
    }

    private static class Multiplexor<T> implements InvocationHandler
    {
        final Class<T> _cls;
        final T _a;
        final T _b;

        Multiplexor(Class<T> cls, T a, T b)
        {
            _cls = cls;
            _a = a;
            _b = b;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args)
                throws Throwable
        {
            method.invoke(_a, args);
            method.invoke(_b, args);
            return null;
        }
    }

    public static <T> T addListener(Class<T> cls, T a, T b)
    {
        if (a == null) return b;
        if (b == null) return a;
        assert cls.isInterface();
        Multiplexor<T> multiplexor = new Multiplexor<T>(cls, a, b);
        T proxy = cls.cast(
                Proxy.newProxyInstance(cls.getClassLoader(), new Class[]{cls}, multiplexor));
        return proxy;
    }

    public static <T> T removeListener(Class<T> cls, T l, T oldl)
    {
        if (l == oldl || l == null) {
            return null;
        }
        if (Proxy.isProxyClass(l.getClass())) {
            InvocationHandler handler = Proxy.getInvocationHandler(l);
            if (handler instanceof Multiplexor) {
                @SuppressWarnings("unchecked")
                Multiplexor<T> multiplexor = (Multiplexor<T>)handler;
                assert multiplexor._cls == cls;
                return removeListener(l, multiplexor, oldl);
            }
        }
        return oldl;
    }

    private static <T> T removeListener(T l, Multiplexor<T> multiplexor, T oldl)
    {
        if (oldl == multiplexor._a) return multiplexor._b;
        if (oldl == multiplexor._b) return multiplexor._a;
        T a = removeListener(multiplexor._cls, multiplexor._a, oldl);
        T b = removeListener(multiplexor._cls, multiplexor._b, oldl);
        if (a == multiplexor._a && b == multiplexor._b) return l;
        return addListener(multiplexor._cls, a, b);
    }
}
