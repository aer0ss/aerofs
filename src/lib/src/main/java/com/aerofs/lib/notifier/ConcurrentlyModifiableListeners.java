package com.aerofs.lib.notifier;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

import com.aerofs.lib.Util;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * THIS CLASS IS OBSOLETE. CONSIDER USING AND IMPROVING Notifier.java INSTEAD.
 *
 * TODO (WW) this class is duplicate with Notifier in functionality. Consider refactoring.
 *
 * A utility class for listener registration and notification. Support copy-on-write on the
 * listeners list. It iterates the listeners in the same order as the order in which listeners
 * are added.
 */
public class ConcurrentlyModifiableListeners<T>
{
    private LinkedHashSet<T> _ls = new LinkedHashSet<T>();
    private int _iterators;
    private boolean _copiedOnWrite;

    /**
     * @return a newly created ConcurrentlyModifiableListeners object
     */
    public static <T> ConcurrentlyModifiableListeners<T> create()
    {
        return new ConcurrentlyModifiableListeners<T>();
    }

    /**
     * this doesn't affect any ongoing iteration over the listeners
     */
    public void addListener_(@Nonnull T l)
    {
        copyOnWrite(false);
        if (_ls.isEmpty()) beforeAddFirstListener_();
        Util.verify(_ls.add(l));
    }

    /**
     * this doesn't affect any ongoing iteration over the listeners
     */
    public void removeListener_(@Nonnull T l)
    {
        copyOnWrite(false);
        _ls.remove(l);
        if (_ls.isEmpty()) afterRemoveLastListener_();
    }

    /**
     * @return null if there's no listener
     */
    public @Nullable T removeFirstListener_()
    {
        copyOnWrite(false);
        Iterator<T> iter = _ls.iterator();
        if (iter.hasNext()) {
            T l = iter.next();
            iter.remove();
            if (_ls.isEmpty()) afterRemoveLastListener_();
            return l;
        } else {
            return null;
        }
    }

    /**
     * this doesn't affect any ongoing iteration over the listeners
     */
    public void removeAll_()
    {
        copyOnWrite(true);
        _ls.clear();
        afterRemoveLastListener_();
    }

    private void copyOnWrite(boolean removeAll)
    {
        if (_iterators != 0) {
            _copiedOnWrite = true;
            // iteration is going on. create a new copy of the set to avoid
            // concurrent modification exception
            assert _iterators > 0;
            _ls = removeAll ? new LinkedHashSet<T>() : new LinkedHashSet<T>(_ls);
        }
    }

    /**
     * Usage:
     *
     *      try {
     *          for (T l : startIteratingListeners_()) {
     *              ...
     *          }
     *      } finally {
     *          endIterating_();
     *      }
     *
     * The returned set iterates the elements in the insertion order. It's
     * immutable during the iteration, even if the listeners list is updated
     * during iteration (in which case endIterating_() returns true).
     */
    public Set<T> beginIterating_()
    {
        _iterators++;
        return Collections.unmodifiableSet(_ls);
    }

    /**
     * @return true if the listeners list has changed during the iteration
     */
    public boolean endIterating_()
    {
        assert _iterators > 0;
        _iterators--;

        boolean ret = _copiedOnWrite;
        _copiedOnWrite = false;

        return ret;
    }

    protected void beforeAddFirstListener_()
    {
    }

    protected void afterRemoveLastListener_()
    {
    }
}
