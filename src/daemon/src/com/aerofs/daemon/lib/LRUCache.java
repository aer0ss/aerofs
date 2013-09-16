package com.aerofs.daemon.lib;

import java.io.PrintStream;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map.Entry;

import com.aerofs.lib.IDumpStatMisc;
import com.aerofs.lib.Util;

import javax.annotation.Nullable;

/**
 * hash is used to compare keys
 */
public class LRUCache<K, V> implements IDumpStatMisc
{
    private class Impl extends LinkedHashMap<K, V> {
        private static final long serialVersionUID = 1L;

        Impl()
        {
            // 0.75 is the default load factor
            super(_capacity, 0.75f, true);
        }

        @Override
        protected boolean removeEldestEntry(Entry<K, V> en)
        {
            if (size() > LRUCache.this._capacity) {
                _evt++;
                if (_el != null) _el.evicted_(en.getKey(), en.getValue());
                return true;
            } else {
                return false;
            }
        }
    }

    public static interface IEvictionListener<K, V> {
        /**
         * called whenever an element is evicted or replaced by a different
         * value. equals() is used to test equality. Note: it's *not* called
         * if on remove() or invalidate*()
         */
        void evicted_(K k, V v);
    }

    public static interface IDataReader<K, V> {
        /**
         * @return null to indicate that the value is a null-value if cacheNull
         * is supported, or the value doesn't exist otherwise
         */
        @Nullable V read_(K key) throws SQLException;
    }

    private final Impl _impl;
    private final int _capacity;
    private final boolean _cacheNull;
    private final @Nullable IEvictionListener<K, V> _el;

    private int _miss;
    private int _hit;
    private int _evt;

    public LRUCache(int capacity)
    {
        this(false, capacity, null);
    }

    public LRUCache(boolean cacheNull, int capacity)
    {
        this(cacheNull, capacity, null);
    }

    public LRUCache(boolean cacheNull, int capacity, @Nullable IEvictionListener<K, V> rl)
    {
        assert capacity > 0;
        _capacity = capacity;
        _cacheNull = cacheNull;
        _el = rl;
        _impl = new Impl();

    }

    @Nullable public V get_(K k, IDataReader<K, V> reader) throws SQLException
    {
        assert k != null;
        if (_impl.containsKey(k)) {
            _hit++;
            return _impl.get(k);
        } else {
            _miss++;
            V v = reader.read_(k);
            if (v != null || _cacheNull) {
                Util.verify(_impl.put(k, v) == null);
            }
            return v;
        }
    }

    /**
     * @return null if no entry exists, OR if the value is null
     */
    @Nullable public V get_(K k)
    {
        assert k != null;
        if (_impl.containsKey(k)) {
            _hit++;
            return _impl.get(k);
        } else {
            _miss++;
            return null;
        }
    }

    public void put_(K k, V v)
    {
        assert k != null;
        assert v != null || _cacheNull;

        if (_el != null && _impl.containsKey(k)) {
            V old = _impl.get(k);
            if (!(old == v || (old != null && old.equals(v)))) {
                _el.evicted_(k, old);
            }
        }

        _impl.put(k, v);
    }

    /**
     * remove the entry if it exists. Otherwise does nothing and returns null
     */
    @Nullable public V invalidate_(K k)
    {
        assert k != null;
        return _impl.remove(k);
    }

    /**
     * remove all entries
     */
    public void invalidateAll_()
    {
        _impl.clear();
    }

    /**
     * caller must guarantee the entry exists. Otherwise, use invalidate_
     */
    public void remove_(K k)
    {
        assert _impl.containsKey(k);
        _impl.remove(k);
    }

    @Override
    public void dumpStatMisc(String indent, String indentUnit, PrintStream ps)
    {
        float mr = (_miss + _hit) == 0 ? 0 : ((float) _miss) / (_miss + _hit);
        ps.println(indent + _impl.size() + " (" + _capacity + ") miss " +
                _miss + " hit " + _hit + " " + mr + " evt " + _evt);
    }
}
