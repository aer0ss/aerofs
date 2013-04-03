/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.synctime;

import com.aerofs.base.id.DID;
import com.aerofs.base.id.OID;
import com.aerofs.lib.id.CID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOCID;
import com.aerofs.testlib.AbstractTest;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterators;
import com.google.common.collect.Sets;
import com.google.common.collect.Table;
import com.google.common.collect.Table.Cell;
import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TestMemoryLimitedLinkedHashMapBasedTable extends AbstractTest
{
    int MAX_TABLE_SIZE = 20;

    // TODO (MJ) address randomness of the table by mocking the Random generator
    Table<DID, SOCID, Long> table
            = new MemoryLimitedLinkedHashMapBasedTable<DID, SOCID, Long>(MAX_TABLE_SIZE);

    // when loading the table, select from among these DIDs
    DID[] dids = new DID[] {DID.generate(), DID.generate(), DID.generate(), DID.generate()};


    @Test
    public void whenExceedingTheCapacityOfTheTable_OldestCellOfARandomRowShouldBeRemoved()
    {
        loadTableWithRandomKeysAndValues(MAX_TABLE_SIZE);
        assertEquals(MAX_TABLE_SIZE, table.size());

        // Record the set of cells before adding another cell beyond the space limit
        Set<Cell<DID, SOCID, Long>> cellsBefore;
        cellsBefore = ImmutableSet.copyOf(table.cellSet());

        // Add another cell to the table, and assert
        // 1) that its size doesn't change
        // 2) that the element removed was the oldest value in its randomly-selected row
        table.put(dids[0], generateSOCID(), System.currentTimeMillis());

        assertEquals(MAX_TABLE_SIZE, table.size());

        Set<Cell<DID, SOCID, Long>> cellsDiff = Sets.difference(cellsBefore, table.cellSet());
        final Cell<DID, SOCID, Long> removedCell = Iterators.getOnlyElement(cellsDiff.iterator());

        for (Long l : table.row(removedCell.getRowKey()).values()) {
            assertTrue(removedCell.getValue() < l);
        }
    }

    private void loadTableWithRandomKeysAndValues(int valuesCount)
    {
        int i = 0;
        while (table.size() < valuesCount) {
            // Round-robin select from the DIDs above
            DID did = dids[i++ % dids.length];
            SOCID socid = generateSOCID();
            // N.B. the values are unique
            table.put(did, socid, (long) table.size());
        }
    }

    private SOCID generateSOCID()
    {
        return new SOCID(new SIndex(7), OID.generate(), CID.META);
    }
}
