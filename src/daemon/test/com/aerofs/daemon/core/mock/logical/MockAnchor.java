package com.aerofs.daemon.core.mock.logical;

import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ds.OA.Type;
import com.aerofs.daemon.core.store.Store;
import com.aerofs.lib.Util;
import com.aerofs.lib.ex.ExExpelled;
import com.aerofs.lib.ex.ExNotDir;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.lib.id.OID;
import com.aerofs.lib.Path;
import com.aerofs.lib.id.SID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.id.UniqueID;

import java.sql.SQLException;

import static com.aerofs.daemon.core.mock.logical.MockDir.mockPhysicalFolder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * See MockRoot for usage.
 */
public class MockAnchor extends AbstractMockLogicalObject
{
    private final AbstractMockLogicalObject[] _children;

    public MockAnchor(String name, AbstractMockLogicalObject ... children)
    {
        this(name, false, children);
    }

    public MockAnchor(String name, boolean expelled, AbstractMockLogicalObject ... children)
    {
        super(name, Type.ANCHOR, new OID(UniqueID.generate()), expelled);

        _children = children;
    }

    @Override
    protected void mockRecursivelyTypeSpecific(OA oa, MockServices ms)
            throws SQLException, ExNotFound, ExNotDir, ExExpelled
    {
        when(oa.isAnchor()).thenReturn(true);
        when(oa.isDirOrAnchor()).thenReturn(true);

        mockPhysicalFolder(oa);

        SID sid = SID.anchorOID2storeSID(oa.soid().oid());
        Store s = mockStore(sid, ms.ds.resolve_(oa), _children, ms);

        SIndex sidx = s.sidx();
        SOID soidRoot = new SOID(sidx, OID.ROOT);
        when(ms.ds.getChildren_(oa.soid())).thenThrow(new ExNotDir());

        if (_expelled) {
            when(ms.ds.followAnchorNullable_(oa)).thenReturn(null);
            when(ms.ds.followAnchorThrows_(oa)).thenThrow(new ExExpelled());
        } else {
            when(ms.ds.followAnchorNullable_(oa)).thenReturn(soidRoot);
            when(ms.ds.followAnchorThrows_(oa)).thenReturn(soidRoot);
        }
    }

    /**
     * @param path the path to the anchor of the store, or empty for root store.
     */
    static Store mockStore(SID sid, Path path, AbstractMockLogicalObject[] children, MockServices ms)
            throws ExNotFound, SQLException, ExNotDir, ExExpelled
    {
        // generate a random, unused sidx.
        SIndex sidx;
        do {
            sidx = new SIndex(Util.rand().nextInt() % 1000);
        } while (ms.sidx2s != null && ms.sidx2s.getNullable_(sidx) != null);

        Store s = mock(Store.class);

        when(s.sidx()).thenReturn(sidx);

        if (ms.sid2sidx != null) when(ms.sid2sidx.getNullable_(sid)).thenReturn(sidx);
        if (ms.sidx2s != null) when(ms.sidx2s.getNullable_(sidx)).thenReturn(s);

        new MockDir(OA.ROOT_DIR_NAME, OID.ROOT, children).mockRecursively(sidx, null, path, ms);

        return s;
    }
}
