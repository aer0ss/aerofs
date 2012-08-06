package com.aerofs.daemon.core.mock.logical;

import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ds.OA.Type;
import com.aerofs.daemon.core.store.MapSIndex2Store;
import com.aerofs.daemon.lib.db.IMetaDatabase;
import com.aerofs.daemon.lib.db.IStoreDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.ex.ExAlreadyExist;
import com.aerofs.lib.ex.ExExpelled;
import com.aerofs.lib.ex.ExNotDir;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.lib.id.OID;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.Path;
import com.aerofs.lib.id.SIndex;
import org.mockito.ArgumentMatcher;

import javax.annotation.Nullable;

import java.sql.SQLException;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Matchers.argThat;

/**
 * See MockRoot for usage.
 */
public abstract class AbstractMockLogicalObject
{
    private final String _name;
    private final Type _type;
    private final OID _oid;
    protected final boolean _expelled;

    AbstractMockLogicalObject(String name, Type type, OID oid, boolean expelled)
    {
        _name = name;
        _type = type;
        _oid = oid;
        _expelled = expelled;
    }

    /**
     * The collection of services the client requires to mock along with the mocked logical objects.
     * All the fields except {@code ds} are optional.
     */
    protected static class MockServices
    {
        final DirectoryService ds;
        final @Nullable MapSIndex2Store sidx2s;
        final @Nullable IMapSID2SIndex sid2sidx;
        final @Nullable IMapSIndex2SID sidx2sid;
        final @Nullable IStoreDatabase im_sdb;
        final @Nullable IMetaDatabase im_mdb;

        MockServices(DirectoryService ds, IMapSID2SIndex sid2sidx, IMapSIndex2SID sidx2sid,
                MapSIndex2Store sidx2s, IStoreDatabase inMemory_sdb, IMetaDatabase inMemory_mdb)
        {
            this.sid2sidx = sid2sidx;
            this.sidx2sid = sidx2sid;
            this.sidx2s = sidx2s;
            this.im_sdb = inMemory_sdb;
            this.im_mdb = inMemory_mdb;
            this.ds = ds;
        }
    }

    /**
     * @return The OA of the current object being mocked
     */
    protected OA mockRecursively(SIndex sidx, OID oidParent, Path pParent, MockServices ms)
            throws SQLException, ExNotFound, ExNotDir, ExExpelled
    {
        SOID soid = new SOID(sidx, _oid);
        boolean root = _oid.equals(OID.ROOT);
        Path path = root ? pParent : pParent.append(_name);

        ////////
        // mock OA

        OA oa = mock(OA.class);
        when(oa.soid()).thenReturn(soid);
        when(oa.name()).thenReturn(_name);
        when(oa.type()).thenReturn(_type);
        when(oa.isExpelled()).thenReturn(_expelled);
        when(oa.parent()).thenReturn(root ? _oid : oidParent);

        // Keep optional in-memory DB consistent with mock object tree
        if (ms.im_mdb != null) {
            try {
                ms.im_mdb.createOA_(sidx, _oid, root ? _oid : oidParent, _name,
                        _type, _expelled ? OA.FLAG_EXPELLED_ORG : 0, mock(Trans.class));
            } catch (ExAlreadyExist e) {
                assert false;
            }
        }

        ////////
        // wire services

        when(ms.ds.getOA_(soid)).thenReturn(oa);
        when(ms.ds.getOANullable_(soid)).thenReturn(oa);
        when(ms.ds.getAliasedOANullable_(soid)).thenReturn(oa);
        when(ms.ds.getAliasedOANullable_(soid)).thenReturn(oa);
        when(ms.ds.resolve_(oa)).thenReturn(path);
        when(ms.ds.resolveNullable_(soid)).thenReturn(path);
        when(ms.ds.resolveThrows_(soid)).thenReturn(path);
        // The path of a root object should be resolved into the anchor's SOID. So we skip mocking
        // the path resolution for roots.
        if (!root) mockPathResolution(ms.ds, path, soid);

        ////////
        // mock type specific stuff

        mockRecursivelyTypeSpecific(oa, ms);
        // verify that mockRecursivelyTypeSpecific did the right job
        assert oa.isAnchor() || oa.isDir() == oa.isDirOrAnchor();
        int trueCount = 0;
        trueCount += oa.isAnchor() ? 1 : -1;
        trueCount += oa.isFile() ? 1 : -1;
        trueCount += oa.isDir() ? 1 : -1;
        assert trueCount == -1;

        return oa;
    }

    /**
     * A Mockito argument matcher for matching Paths in a case-insensitive way (as the
     * DirectoryService does)
     */
    private static class IsEqualPathIgnoringCase extends ArgumentMatcher<Path>
    {
        private final Path _path;

        IsEqualPathIgnoringCase(Path path) {_path = path;}

        // In reality, when passed a path of same name, but different case, the DS returns the SOID
        // for the case that is stored in the DB. This matcher helps reflect that behaviour
        @Override
        public boolean matches(Object path) {
            return _path.equalsIgnoreCase((Path) path);
        }
    }

    static void mockPathResolution(DirectoryService ds, Path path, SOID soid)
            throws SQLException, ExNotFound
    {
        when(ds.resolveNullable_(argThat(new IsEqualPathIgnoringCase(path)))).thenReturn(soid);
        when(ds.resolveThrows_(argThat(new IsEqualPathIgnoringCase(path)))).thenReturn(soid);
    }

    /**
     * When this method is called, all the mocks and services have been set up for the current
     * logical object.
     *
     * @param oa OA of the current object
     */
    protected abstract void mockRecursivelyTypeSpecific(OA oa, MockServices ms)
            throws SQLException, ExNotFound, ExNotDir, ExExpelled;
}
