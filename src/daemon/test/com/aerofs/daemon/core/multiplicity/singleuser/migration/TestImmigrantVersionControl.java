package com.aerofs.daemon.core.multiplicity.singleuser.migration;

import com.aerofs.ids.DID;
import com.aerofs.ids.OID;
import com.aerofs.ids.UniqueID;
import com.aerofs.daemon.core.AbstractTestVersionControl;
import com.aerofs.daemon.core.AbstractVersionControl;
import com.aerofs.daemon.core.migration.ImmigrantVersionControl;
import com.aerofs.daemon.core.store.MapSIndex2Contributors;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.ver.IImmigrantVersionDatabase;
import com.aerofs.daemon.lib.db.ver.IVersionDatabase;
import com.aerofs.daemon.lib.db.ver.ImmigrantTickRow;
import com.aerofs.lib.Tick;
import com.aerofs.lib.Version;
import com.aerofs.lib.db.MockDBIterator;
import com.aerofs.lib.id.*;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.sql.SQLException;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

public class TestImmigrantVersionControl extends AbstractTestVersionControl<ImmigrantTickRow>
{
    @Mock IImmigrantVersionDatabase ivdb;
    @Mock MapSIndex2Contributors sidx2contrib;
    @InjectMocks ImmigrantVersionControl ivc;

    Tick initialGreatestTick = new Tick(1000);
    SOCID socid = new SOCID(new SIndex(1), new OID(UniqueID.generate()), new CID(100));
    DID did = new DID(UniqueID.generate());
    Tick tick = new Tick(123);
    DID immDid = new DID(UniqueID.generate());
    Tick immTick = new Tick(456);

    @Override
    protected AbstractVersionControl<ImmigrantTickRow> createVersionControl() {
        return ivc;
    }

    @Override
    protected IVersionDatabase<ImmigrantTickRow> createVersionDatabase() {
        return ivdb;
    }

    @Override
    protected MockDBIterator<ImmigrantTickRow> createDBIterator() {
        return new MockDBIteratorImmTickRow();
    }

    @Before
    public void setup() throws Exception
    {
        when(ivdb.getGreatestTick_()).thenReturn(initialGreatestTick);

        ivc.init_();
    }

    @Test
    public void shouldAddImmVersionToDatabase() throws SQLException
    {
        when(ivdb.isTickKnown_(socid, did, tick)).thenReturn(false);
        assertTrue(ivc.immigrantTickReceived_(socid, immDid, immTick, did, tick, t));
        verify(ivdb).addImmigrantVersion_(socid, immDid, immTick, did, tick, t);
    }

    @Test
    public void shouldNotAddImmVersionIfTickIsKnown() throws SQLException
    {
        when(ivdb.isTickKnown_(socid, did, tick)).thenReturn(true);
        assertFalse(ivc.immigrantTickReceived_(socid, immDid, immTick, did, tick, t));
        verify(ivdb, never()).addImmigrantVersion_((SOCID) any(), (DID) any(),
                (Tick) any(), (DID) any(), (Tick) any(), (Trans) any());
    }

    @Test
    public void shouldUpdateMyImmVersionToDatabaseAndIncrementGreatestTick()
            throws SQLException
    {
        Tick curGreatestTick = initialGreatestTick.incNonAlias();
        when(ivdb.isTickKnown_(socid, did, tick)).thenReturn(false);
        ivc.createLocalImmigrantVersions_(socid, Version.of(did, tick), t);
        verify(ivdb).addImmigrantVersion_(socid, cfgLocalDID.get(), curGreatestTick, did, tick, t);

        Tick newTick = tick.incAlias();
        curGreatestTick = curGreatestTick.incNonAlias();
        ivc.createLocalImmigrantVersions_(socid, Version.of(did, newTick), t);
        verify(ivdb).addImmigrantVersion_(socid, cfgLocalDID.get(), curGreatestTick, did, newTick,
                t);
    }


    private static class MockDBIteratorImmTickRow
                extends MockDBIterator<ImmigrantTickRow>
    {
        public MockDBIteratorImmTickRow()
        {
            super(new ImmigrantTickRow[] {
                    new ImmigrantTickRow(new OID(UniqueID.generate()), new CID(1),
                            new DID(UniqueID.generate()), new Tick(1), new Tick(2)),
                    new ImmigrantTickRow(new OID(UniqueID.generate()), new CID(1),
                            new DID(UniqueID.generate()), new Tick(3), new Tick(4)),
                    new ImmigrantTickRow(new OID(UniqueID.generate()), new CID(1),
                            new DID(UniqueID.generate()), new Tick(7), new Tick(9)),
                    new ImmigrantTickRow(new OID(UniqueID.generate()), new CID(1),
                            new DID(UniqueID.generate()), new Tick(28), new Tick(23)),
            });
        }
    }

}
