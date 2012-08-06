package com.aerofs.daemon.core.mock.logical;

import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ds.OA.Type;
import com.aerofs.daemon.core.phy.IPhysicalFolder;
import com.aerofs.lib.ex.ExExpelled;
import com.aerofs.lib.ex.ExNotDir;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.lib.id.OID;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.Path;
import com.aerofs.lib.id.UniqueID;

import java.sql.SQLException;
import java.util.HashSet;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * See MockRoot for usage.
 */
public class MockDir extends AbstractMockLogicalObject
{
    private final AbstractMockLogicalObject[] _children;

    public MockDir(String name, AbstractMockLogicalObject ... children)
    {
        this(name, new OID(UniqueID.generate()), children);
    }

    public MockDir(String name, OID oid, AbstractMockLogicalObject[] children)
    {
        super(name, Type.DIR, oid, false);
        _children = children;
    }

    @Override
    protected void mockRecursivelyTypeSpecific(OA oa, MockServices ms)
            throws SQLException, ExNotFound, ExNotDir, ExExpelled
    {
        when(oa.isDir()).thenReturn(true);
        when(oa.isDirOrAnchor()).thenReturn(true);

        mockPhysicalFolder(oa);

        SOID soid = oa.soid();
        Path path = ms.ds.resolveNullable_(oa.soid());

        HashSet<OID> oidChildren = new HashSet<OID>(_children.length);
        for (AbstractMockLogicalObject child : _children) {
            OA oaChild = child.mockRecursively(soid.sidx(), soid.oid(), path, ms);
            SOID soidChild = oaChild.soid();
            String nameChild = oaChild.name();
            oidChildren.add(soidChild.oid());
            when(ms.ds.getChild_(soid.sidx(), soid.oid(), nameChild)).thenReturn(soidChild.oid());
        }

        when(ms.ds.getChildren_(soid)).thenReturn(oidChildren);
    }

    static void mockPhysicalFolder(OA oa)
    {
        assert oa.isDir() || oa.isAnchor();

        // don't use .then(RETURRN_MOCKS) here so the client can verify on the mocked object
        when(oa.physicalFolder()).thenReturn(mock(IPhysicalFolder.class));
    }
}
