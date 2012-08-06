package com.aerofs.daemon.lib.db.trans;

import java.util.Map;

import com.google.common.collect.Maps;

/**
 * Very similar to java.lang.ThreadLocal, instances of this class hold objects whose lifespan is
 * scoped by transactions. This example shows how to attach a unique ID to each transaction.
 *
 *      class TransID {
 *          private static int s_nextID;
 *
 *          private static TransLocal<Integer> s_tl = new TransLocal<Integer>() {
 *              @Override
 *              protected Integer initialValue(Trans t) {
 *                  return s_nextID++;
 *              }
 *          };
 *
 *          public static int getTransID_(Trans t) {
 *              return s_tl.get(t);
 *          }
 *      }
 */
public abstract class TransLocal<T>
{
    /**
     * @see ThreadLocal#get()
     */
    public T get(Trans t)
    {
        final Map<TransLocal<?>, Object> map = getMap(t);
        if (map != null) {
            Object o = map.get(this);
            if (o != null) return unmaskNull(o);
        }
        T value = initialValue(t);
        set(t, value);
        return value;
    }

    /**
     * @see ThreadLocal#set(T)
     */
    public void set(Trans t, T value)
    {
        getOrCreateMap(t).put(this, maskNull(value));
    }

    /**
     * @see ThreadLocal#remove()
     */
    public void remove(Trans t)
    {
        final Map<TransLocal<?>, Object> map = getMap(t);
        if (map != null) map.remove(this);
    }

    /**
     * @see ThreadLocal#initialValue()
     */
    protected T initialValue(Trans t)
    {
        return null;
    }

    // The NULL object allows users to use null as TransLocal values. We could alternatively call
    // map.containsKey() to test null values but the NULL object avoids this extra map lookup.
    private static final Object NULL = new Object();

    private Object maskNull(T value)
    {
        return (value == null) ? NULL : value;
    }

    @SuppressWarnings("unchecked")
    private T unmaskNull(Object o) {
        return (o == NULL) ? null : (T) o;
    }

    private static Map<TransLocal<?>, Object> getMap(Trans t)
    {
        return t._transLocals;
    }

    private static Map<TransLocal<?>, Object> getOrCreateMap(Trans t)
    {
        if (t._transLocals == null) t._transLocals = Maps.newHashMap();
        return getMap(t);
    }
}
