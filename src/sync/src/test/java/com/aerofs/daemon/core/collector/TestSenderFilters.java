package com.aerofs.daemon.core.collector;

import com.aerofs.daemon.lib.db.SyncSchema;
import com.aerofs.ids.DID;
import com.aerofs.testlib.InMemorySQLiteDBCW;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

import static org.mockito.Mockito.*;
import static org.junit.Assert.*;
import static com.aerofs.daemon.core.collector.SenderFilterIndex.BASE;

import com.aerofs.daemon.core.collector.SenderFilters.SenderFilterAndIndex;
import com.aerofs.daemon.lib.db.ISenderFilterDatabase;
import com.aerofs.daemon.lib.db.SenderFilterDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.bf.BFOID;
import com.aerofs.ids.OID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.ids.UniqueID;
import com.aerofs.testlib.AbstractTest;

import java.sql.Statement;

public class TestSenderFilters extends AbstractTest
{
    private final InMemorySQLiteDBCW dbcw = new InMemorySQLiteDBCW();

    @Spy  ISenderFilterDatabase sfdb = new SenderFilterDatabase(dbcw);
    @Mock TransManager tm;

    @InjectMocks SenderFilters.Factory sfFact;

    @Mock Trans t;

    // System under Test
    private SenderFilters sf;

    @Before
    public void setup() throws Exception
    {
        when(tm.begin_()).thenReturn(t);
        dbcw.init_();
        try (Statement s = dbcw.getConnection().createStatement()) {
            new SyncSchema().create_(s, dbcw);
        }

        // Because the SenderFilters constructor uses the db, sf must be
        // instantiated *after* the db is initialized
        sf = sfFact.create_(new SIndex(5));
    }

    @After
    public void tearDown() throws Exception
    {
        dbcw.fini_();
    }

    @Test
    public void shouldReturnExpectedFilterWhenLatestFilterIsBase()
        throws Exception
    {
        OID [] oids = new OID[] { new OID(UniqueID.generate()),
                                  new OID(UniqueID.generate()) };

        // Insert oids into the SystemUtil Under Test
        for (OID oid: oids) {
            sf.objectUpdated_(oid, t);
        }
        BFOID filterReturned = sf.get_(mock(DID.class), false)._filter;

        // Load the expected filter
        BFOID filterExpected = new BFOID();
        for (OID oid : oids) filterExpected.add_(oid);

        assertEquals(filterExpected, filterReturned);
    }

    @Test
    public void shouldReturnNullWhenNoFiltersInDBAndFromBaseIsFalse() throws Exception
    {
        assertNull(sf.get_(mock(DID.class), false));
    }

    @Test
    public void shouldReturnNullWhenNoFiltersInDBAndFromBaseIsTrue() throws Exception
    {
        assertNull(sf.get_(mock(DID.class), true));
    }


    /**
     * SFI = SenderFilterIndex
     * did's 1 and 2 are used in such a way with the SenderFilters that
     * the order of their respective SenderFilterIndexes should be predictable
     */
    @Test
    public void shouldReturnSFIOfBaseLessThanSFIOfDID1LessThanSFIOfDID2()
        throws Exception
    {
        DID did1 = new DID(UniqueID.generate());
        DID did2 = new DID(UniqueID.generate());

        makeDID1IndexMiddleSFIAndDID2IndexLatest(did1, did2);

        SenderFilterIndex sfi1 = sf.get_(did1, false)._sfidx;
        SenderFilterIndex sfi2 = sf.get_(did2, false)._sfidx;

        assertTrue(BASE.compareTo(sfi1) < 0 && sfi1.compareTo(sfi2) < 0);
    }

    @Test
    public void shouldMakeBaseFilterNonEmptyAndLatestEmptyAndDIDIndexesLatest() throws Exception
    {
        DID did = new DID(UniqueID.generate());
        makeBaseNonEmptyAndLatestEmptyAndDIDIndexesLatest(did);

        // get_ returns null when did indexes an empty filter
        SenderFilterAndIndex sfai = sf.get_(did, false);
        assertNull(sfai);
        // did indexes the latest filter if getSenderFilters is never called
        verify(sfdb, never()).getSenderFilters_((SIndex) any(),
                                              (SenderFilterIndex) any(),
                                              (SenderFilterIndex) any());
    }

    @Test
    public void shouldNotRemoveEmptyLatestWhenRequestedFiltersFromBase() throws Exception
    {
        DID did = new DID(UniqueID.generate());
        makeBaseNonEmptyAndLatestEmptyAndDIDIndexesLatest(did);

        /////////
        // did indexes the empty latest filter, but ask for all filters from BASE
        SenderFilterAndIndex sfai = sf.get_(did, true);
        // Latest should be at position 1. Verify did indexed latest
        assertEquals(new SenderFilterIndex(BASE.getLong()).plusOne(), sfai._sfidx);

        /////////
        // Tell the Sender that the filter and index were received successfully
        sf.update_(did, sfai._sfidx, sfai._updateSeq);

        // Because the indexed filter was empty (and latest)
        // 1) it should not be deleted
        // 2) the index for did should not change
        verify(sfdb, never()).deleteSenderFilter_((SIndex)any(), eq(sfai._sfidx), eq(t));
        // N.B. must pass fromBase == true otherwise will get a null SenderFilterAndIndex
        //      as did indexes the empty latest filter
        assertEquals(sfai._sfidx, sf.get_(did, true)._sfidx);
    }

    @Test
    public void shouldReturnBaseFilterOnwardAndMiddleIdxWhenFromBaseIsTrueAndDIDIsBetweenLatestAndBase()
        throws Exception
    {
        // These three OIDs will be added to filters in the db.
        OID [] oids = new OID[] { new OID(UniqueID.generate()),
                                  new OID(UniqueID.generate()),
                                  new OID(UniqueID.generate()),
                                };
        DID did1 = new DID(UniqueID.generate());
        DID did2 = new DID(UniqueID.generate());

        makeDID1IndexMiddleSFIAndDID2IndexLatest(did1, did2, oids);
        SenderFilterIndex sfiExpected = sf.get_(did1, false)._sfidx;

        ////////
        // Simulate that did1 needs all filters from BASE onward
        SenderFilterAndIndex sfaiActual = sf.get_(did1, true);

        BFOID filterExpected = new BFOID();
        for (OID oid : oids) filterExpected.add_(oid);

        assertEquals(sfiExpected, sfaiActual._sfidx);
        assertEquals(filterExpected, sfaiActual._filter);
    }

    @Test
    public void shouldNotMergeCarelessly() throws Exception
    {
        DID did = DID.generate();
        OID oid = OID.generate();

        // for META tick
        sf.objectUpdated_(oid, t);

        // gv from peer
        SenderFilterAndIndex sfi;
        sfi = sf.get_(did, false);
        assertNotNull(sfi);
        assertTrue(sfi._filter.contains_(oid));

        // for CONTENT tick
        sf.objectUpdated_(oid, t);

        // filter update from peer after gv
        sf.update_(did, sfi._sfidx, sfi._updateSeq);

        // gv from peer
        sfi = sf.get_(did, false);

        // ensure that the last filter was not merged
        assertNotNull(sfi);
        assertTrue(sfi._filter.contains_(oid));
    }

    /**
     * This method causes the SenderFilters object to create 3 filters, and
     * associates did1 with the middle filter (its index between BASE and latest)
     * and associates did2 with the latest filter.
     * @param oids an array of 3 OIDs; one oid is inserted into each of the 3 filters
     */
    private void makeDID1IndexMiddleSFIAndDID2IndexLatest(DID did1,
                                                          DID did2,
                                                          OID [] oids
                                                          ) throws Exception
    {
        // There will be three filters in the db containing one OID each:
        // - BASE
        // - the one belonging to the given DID
        // - latest
        assertEquals(3, oids.length);
        for (OID oid : oids) assertNotNull(oid);

        // Establish a link between did1 and the BASE filter
        sf.objectUpdated_(oids[0], t);
        SenderFilterAndIndex sfai = sf.get_(did1, false);
        assertEquals(BASE, sfai._sfidx);

        // Move did1 to the next filter and load the filter with an oid
        sf.update_(did1, sfai._sfidx, sfai._updateSeq);
        sf.objectUpdated_(oids[1], t);
        assertTrue(BASE.compareTo(sf.get_(did1, false)._sfidx) < 0);

        // Create a latest filter that did2 indexes
        // - first add did2 to the filter index table, indexing BASE
        sfai = sf.get_(did2, false);
        assertEquals(BASE, sfai._sfidx);
        // - move did2 to a new filter
        sf.update_(did2, sfai._sfidx, sfai._updateSeq);
        // - load the latest filter with final oid
        sf.objectUpdated_(oids[2], t);
    }

    /**
     * For the unit tests that don't need the actual values of the OIDs,
     * this simply loads the method above with three OIDs
     * @throws Exception
     */
    private void makeDID1IndexMiddleSFIAndDID2IndexLatest(DID did1,
                                                          DID did2
                                                          ) throws Exception
    {
        OID [] oids = new OID[] { new OID(UniqueID.generate()),
                                  new OID(UniqueID.generate()),
                                  new OID(UniqueID.generate()),
                                };

        makeDID1IndexMiddleSFIAndDID2IndexLatest(did1, did2, oids);
    }

    /**
     * This method sets up the database where the Base SenderFilter is non-empty
     * and the given DID indexes the empty filter which comes after Base.
     */
    private void makeBaseNonEmptyAndLatestEmptyAndDIDIndexesLatest(DID did) throws Exception
    {
        OID [] oids = new OID[] { new OID(UniqueID.generate()),
                                  new OID(UniqueID.generate()) };

        // Establish a link between did and BASE filter
        for (OID oid : oids) sf.objectUpdated_(oid, t);
        SenderFilterAndIndex sfai = sf.get_(did, false);
        assertEquals(BASE, sfai._sfidx);

        // Move did to point to the next filter (should be 1)
        sf.update_(did, sfai._sfidx, sfai._updateSeq);
    }
}
