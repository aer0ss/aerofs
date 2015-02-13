package com.aerofs.daemon.core;

import com.aerofs.ids.OID;
import com.aerofs.ids.UniqueID;
import com.aerofs.daemon.core.store.MapSIndex2Contributors;
import com.aerofs.daemon.lib.db.ICollectorSequenceDatabase;
import com.aerofs.daemon.lib.db.IMetaDatabase;
import com.aerofs.daemon.lib.db.ver.INativeVersionDatabase;
import com.aerofs.daemon.lib.db.ver.IVersionDatabase;
import com.aerofs.daemon.lib.db.ver.NativeTickRow;
import com.aerofs.lib.Tick;
import com.aerofs.lib.Version;
import com.aerofs.lib.db.MockDBIterator;
import com.aerofs.lib.id.CID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOCID;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.sql.SQLException;

import static org.mockito.Mockito.verify;

public class TestNativeVersionControl extends AbstractTestVersionControl<NativeTickRow>
{
    @Mock INativeVersionDatabase nvdb;
    @Mock ICollectorSequenceDatabase csdb;
    @Mock IMetaDatabase mdb;
    @Mock MapSIndex2Contributors sidx2contrib;
    @InjectMocks NativeVersionControl nvc;

    @Override
    protected AbstractVersionControl<NativeTickRow> createVersionControl()
    {
        return nvc;
    }

    @Override
    protected IVersionDatabase<NativeTickRow> createVersionDatabase()
    {
        return nvdb;
    }

    @Override
    protected MockDBIterator<NativeTickRow> createDBIterator()
    {
        return new MockDBIteratorNativeTickRow();
    }

    @Override
    protected void shouldAddTickRowToVersionDatabase(SIndex sidx, NativeTickRow tr)
            throws SQLException
    {
        verify(nvdb).addKMLVersion_(
                new SOCID(sidx, tr._oid, tr._cid),
                Version.of(cfgLocalDID.get(), tr._tick), t);
    }

    private static class MockDBIteratorNativeTickRow
                    extends MockDBIterator<NativeTickRow>
    {
        public MockDBIteratorNativeTickRow()
        {
            super(new NativeTickRow[] {
                    new NativeTickRow(new OID(UniqueID.generate()), new CID(1), new Tick(1)),
                    new NativeTickRow(new OID(UniqueID.generate()), new CID(1), new Tick(2)),
                    new NativeTickRow(new OID(UniqueID.generate()), new CID(1), new Tick(5)),
                    new NativeTickRow(new OID(UniqueID.generate()), new CID(1), new Tick(7)),
                    new NativeTickRow(new OID(UniqueID.generate()), new CID(1), new Tick(9)),
            });
        }
    }
}
