package com.aerofs.daemon.core;

import java.sql.SQLException;

import com.aerofs.base.id.DID;
import com.aerofs.daemon.core.store.StoreDeletionOperators;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mock;

import static org.mockito.Mockito.*;

import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.ver.IVersionDatabase;
import com.aerofs.daemon.lib.db.ver.AbstractTickRow;
import com.aerofs.daemon.lib.db.ver.TransLocalVersionAssistant;
import com.aerofs.daemon.lib.db.ver.VersionAssistant;
import com.aerofs.lib.Tick;
import com.aerofs.lib.cfg.CfgLocalDID;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.lib.db.MockDBIterator;
import com.aerofs.lib.id.SIndex;
import com.aerofs.base.id.UniqueID;
import com.aerofs.testlib.AbstractTest;

public abstract class AbstractTestVersionControl<E extends AbstractTickRow> extends AbstractTest
{
    private IVersionDatabase<E> vdb;
    private AbstractVersionControl<E> vc;
    @Mock VersionAssistant va;
    @Mock TransLocalVersionAssistant tlva;
    @Mock StoreDeletionOperators sdo;

    @Mock protected CfgLocalDID cfgLocalDID;
    @Mock protected Trans t;

    SIndex sidx = new SIndex(1);

    /**
     * @return the specialized *VersionControl class under test
     */
    protected abstract AbstractVersionControl<E> createVersionControl();

    /**
     * @return the (optionally mocked) database used by the version control
     */
    protected abstract IVersionDatabase<E> createVersionDatabase();

    protected abstract MockDBIterator<E> createDBIterator();

    @Before
    public void createVCandDBandSetupMocks() throws Exception
    {
        vc = createVersionControl();
        vdb = createVersionDatabase();
        when(cfgLocalDID.get()).thenReturn(new DID(UniqueID.generate()));
        when(tlva.get(any(Trans.class))).thenReturn(va);
    }

    @Test
    public void shouldBackupBeforeDelete() throws Exception
    {
        IDBIterator<E> iter = createDBIterator();
        when(vdb.getMaxTicks_(sidx, cfgLocalDID.get(), Tick.ZERO))
                .thenReturn(iter);

        vc.deleteStore_(sidx, t);

        verify(vdb).deleteTicksAndKnowledgeForStore_(sidx, t);
    }

    @Test
    public void shouldRestoreCorrectTickRows() throws Exception
    {
        MockDBIterator<E> iter = createDBIterator();
        when(vdb.getBackupTicks_(sidx)).thenReturn(iter);

        vc.restoreStore_(sidx, t);

        for (int i = 0; i < iter.elems.length; i++) {
            shouldAddTickRowToVersionDatabase(sidx, iter.elems[i]);
        }
    }

    /**
     * Verify that the *VersionControl correctly adds the tick row to db
     * @throws SQLException
     */
    protected void shouldAddTickRowToVersionDatabase(SIndex sidx, E tr) throws SQLException {}

    @Ignore @Test
    public void shouldRestoreGivenStoreWithSameSIDAsDeleteButDifferentSIndex()
        throws Exception
    {
        // s2.streamId = s1.streamId; s2.sidx != s1.sidx

        // nvc.deleteStore_(s1, t);
        // nvc.restoreStore_(s2, t);
        // assert that the rows restored are as expected
    }


}
