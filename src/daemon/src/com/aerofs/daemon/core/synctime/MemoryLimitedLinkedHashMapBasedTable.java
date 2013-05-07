/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.synctime;

import com.aerofs.base.Loggers;
import com.google.common.base.Supplier;
import com.google.common.collect.ForwardingTable;
import com.google.common.collect.Iterators;
import com.google.common.collect.Maps;
import com.google.common.collect.Table;
import com.google.common.collect.Tables;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Iterables.getFirst;

/**
 * A Guava Table that limits its total space usage by overriding methods
 *  put()
 *  putAll().
 * An obvious shortcoming is that class users can invoke table.row(r).put(c, v), circumventing
 * the space restrictions, but they really ought to use table.put(r,c,v) in the first place.
 */
class MemoryLimitedLinkedHashMapBasedTable<R,C,V> extends ForwardingTable<R,C,V>
{
    private static final Logger l = Loggers.getLogger(MemoryLimitedLinkedHashMapBasedTable.class);

    private final Table<R, C, V> _delegate;
    private final int _maxTableSize;
    private final Random _random;

    MemoryLimitedLinkedHashMapBasedTable(int maxTableSize)
    {
        checkArgument(maxTableSize > 0);
        _maxTableSize = maxTableSize;
        _random = new Random();

        _delegate = Tables.newCustomTable(new HashMap<R, Map<C, V>>(), new Supplier<Map<C, V>>()
        {
            @Override
            public Map<C, V> get()
            {
                // Use a LinkedHashMap for the columns so that the order in which elements were
                // added to the columns is known (i.e. insertion order)
                return Maps.newLinkedHashMap();
            }
        });
    }

    @Override
    protected Table<R, C, V> delegate()
    {
        return _delegate;
    }

    @Override
    public V put(R r, C c, V v)
    {
        V retval = super.put(r, c, v);
        purgeOldestValuesIfOutOfSpace_();
        return retval;
    }

    @Override
    public void putAll(Table<? extends R, ? extends C, ? extends V> t)
    {
        super.putAll(t);
        purgeOldestValuesIfOutOfSpace_();
    }

    private void purgeOldestValuesIfOutOfSpace_()
    {
        // If the size of the table has exceeded the permitted size, remove the first element
        // of the LinkedHashMap for a randomly-chosen row.
        while (size() > _maxTableSize) {
            R rowToPrune = getRowToPrune_();
            // N.B. Recall that the Map<SOCID, Long> are recorded as LinkedHashMap, so the
            // first element of this map is guaranteed to be the oldest for that DID
            C columnToRemove = getFirst(row(rowToPrune).keySet(), null);
            if (columnToRemove != null) {
                // TODO (MJ) may want to report a Meter to Rocklog when objects are evicted,
                // so that we know how well tuned the max table size is
                remove(rowToPrune, columnToRemove);
            } else {
                // The map at rowToPrune was empty, so remove the row from the table
                rowMap().remove(rowToPrune);
            }
        }
    }

    /**
     * @return a randomly chosen row for which its first recorded (c, v) pair will be removed
     * TODO (MJ) there are better application-specific ways to prune this table.
     * e.g. in TTS, we want to remove the object o that minimizes 
     *   (_updateTimes[o,d] - _checkpoint[d]) for all o for all d
     * but that optimization only helps with accuracy of the memory-limited-degraded TTS
     * measurements, and isn't necessary "for now"
     */
    private R getRowToPrune_()
    {
        Set<R> rows = super.rowKeySet();
        Iterator<R> it = rows.iterator();
        Iterators.advance(it, _random.nextInt(rows.size()));
        return it.next();
    }
}