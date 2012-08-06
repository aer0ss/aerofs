package com.aerofs.daemon.core.mock;

import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.core.store.IStores;
import com.aerofs.daemon.core.store.MapSIndex2Store;
import com.aerofs.daemon.core.store.Store;
import com.aerofs.daemon.core.NativeVersionControl;
import com.aerofs.daemon.core.ds.CA;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ds.OA.Type;

import com.aerofs.daemon.core.phy.IPhysicalFile;
import com.aerofs.daemon.core.phy.IPhysicalFolder;

import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.Tick;
import com.aerofs.lib.Version;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.lib.id.*;
import com.google.common.collect.Sets;

import javax.annotation.Nullable;
import org.junit.Ignore;

import java.sql.SQLException;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.*;

/**
 * NOTE: this class is OBSOLETE. please use the com.aerofs.daemon.core.mock package instead.
 */
@Ignore
public class TestUtilCore
{

    public static class ExArbitrary extends RuntimeException
    {
        private static final long serialVersionUID = 1L;
    }

    public static void mockOA(OA oa, SOID soid, Type type, boolean expelled,
        @Nullable OID oidParent, @Nullable String name, @Nullable DirectoryService ds)
        throws SQLException
    {
        when(oa.soid()).thenReturn(soid);
        when(oa.type()).thenReturn(type);
        when(oa.isExpelled()).thenReturn(expelled);
        when(oa.isAnchor()).thenReturn(false);
        when(oa.isDir()).thenReturn(false);
        when(oa.isFile()).thenReturn(false);

        if (name != null) when(oa.name()).thenReturn(name);
        if (oidParent != null) when(oa.parent()).thenReturn(oidParent);

        switch (type) {
            case FILE:
                when(oa.isFile()).thenReturn(true);
                when(oa.cas()).then(RETURNS_MOCKS);
                break;
            case DIR:
                // don't use .then(RETURNS_MOCKS) here so the client can verify
                // on the mocked object
                when(oa.isDir()).thenReturn(true);
                when(oa.physicalFolder()).thenReturn(mock(IPhysicalFolder.class));
                break;
            case ANCHOR:
                // don't use .then(RETURNS_MOCKS) here so the client can verify
                // on the mocked object
                when(oa.isAnchor()).thenReturn(true);
                when(oa.physicalFolder()).thenReturn(mock(IPhysicalFolder.class));
                break;
            default:
                assertFalse(true);
        }

        if (ds != null) {
            // ds can be a spy object hence using doReturn() clause instead of when().
            when(ds.getOANullable_(soid)).thenReturn(oa);
            when(ds.getAliasedOANullable_(soid)).thenReturn(oa);
        }
    }

    public static CA mockCA(OA oa, KIndex kidx, long len, long mtime)
            throws ExNotFound
    {
        CA ca = mock(CA.class);

        // don't use .then(RETURNS_MOCKS) here so the client can verify
        // on the mocked object
        when(ca.physicalFile()).thenReturn(mock(IPhysicalFile.class));

        when(ca.length()).thenReturn(len);
        when(ca.mtime()).thenReturn(mtime);
        when(oa.ca(kidx)).thenReturn(ca);
        when(oa.caThrows(kidx)).thenReturn(ca);

        return ca;
    }

    /**
     * @param len
     * @param mtime
     * @param nvc non-null to mock local versions for those branches
     */
    public static void mockBranches(OA oa, int branches,
        long len, long mtime, @Nullable NativeVersionControl nvc)
            throws SQLException, ExNotFound
    {
        SortedMap<KIndex, CA> cas = new TreeMap<KIndex, CA>();

        int kMaster = KIndex.MASTER.getInt();
        for (int i = kMaster; i < kMaster + branches; i++) {
            KIndex kidx = new KIndex(i);
            SOCKID k = new SOCKID(oa.soid(), CID.CONTENT, kidx);

            // mock CA
            CA ca = mockCA(oa, kidx, len, mtime);
            cas.put(kidx, ca);

            if (nvc != null) {
                // mock local version
                Version vLocal = new Version();
                vLocal.set_(new DID(UniqueID.generate()), new Tick(i + 1));
                when(nvc.getLocalVersion_(k)).thenReturn(vLocal);
            }
        }
        when(oa.cas()).thenReturn(cas);
    }

    public static void mockStore(@Nullable Store s, SID sid, SIndex sidx, SIndex sidxParent,
            @Nullable IStores ss, @Nullable MapSIndex2Store sidx2s, @Nullable
            IMapSID2SIndex sid2sidx, @Nullable IMapSIndex2SID sidx2sid)
            throws ExNotFound, SQLException
    {
        if (s != null) {
            when(s.sidx()).thenReturn(sidx);
        }

        if (ss != null) {
            when(ss.getParent_(sidx)).thenReturn(sidxParent);
            Set<SIndex> children = ss.getChildren_(sidxParent);
            if (children == null) children = Sets.newHashSet();
            children.add(sidx);
            when(ss.getChildren_(sidxParent)).thenReturn(children);
        }

        if (sidx2s != null && s != null) {
            when(sidx2s.getNullable_(sidx)).thenReturn(s);
            when(sidx2s.getThrows_(sidx)).thenReturn(s);
            when(sidx2s.get_(sidx)).thenReturn(s);
        }

        if (sid2sidx != null) {
            when(sid2sidx.get_(sid)).thenReturn(sidx);
            when(sid2sidx.getNullable_(sid)).thenReturn(sidx);
            when(sid2sidx.getAbsent_(eq(sid), any(Trans.class))).thenReturn(sidx);
        }

        if (sidx2sid != null) {
            when(sidx2sid.get_(sidx)).thenReturn(sid);
            when(sidx2sid.getNullable_(sidx)).thenReturn(sid);
            when(sidx2sid.getThrows_(sidx)).thenReturn(sid);
        }
    }

    public static void mockAbsentStore(SIndex sidx, SID sid, @Nullable MapSIndex2Store sidx2s,
            @Nullable IMapSID2SIndex sid2sidx, @Nullable IMapSIndex2SID sidx2sid) throws ExNotFound
    {
        if (sidx2s != null) {
            when(sidx2s.getNullable_(sidx)).thenReturn(null);
            when(sidx2s.getThrows_(sidx)).thenThrow(new ExNotFound());
            when(sidx2s.get_(sidx)).thenThrow(new AssertionError());
        }

        if (sid2sidx != null) {
            when(sid2sidx.getNullable_(sid)).thenReturn(null);
            when(sid2sidx.get_(sid)).thenThrow(new AssertionError());
            when(sid2sidx.getNullable_(sid)).thenReturn(null);
        }

        if (sidx2sid != null) {
            when(sidx2sid.get_(sidx)).thenThrow(new AssertionError());
            when(sidx2sid.getNullable_(sidx)).thenReturn(null);
            when(sidx2sid.getThrows_(sidx)).thenThrow(new ExNotFound());
        }
    }
}
