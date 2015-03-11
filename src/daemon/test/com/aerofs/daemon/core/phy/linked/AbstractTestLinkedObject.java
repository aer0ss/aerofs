package com.aerofs.daemon.core.phy.linked;

import com.aerofs.daemon.core.phy.linked.linker.HashQueue;
import com.aerofs.ids.OID;
import com.aerofs.ids.SID;
import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ds.ResolvedPath;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.core.phy.linked.db.NRODatabase;
import com.aerofs.daemon.core.phy.linked.fid.IFIDMaintainer;
import com.aerofs.daemon.core.phy.linked.linker.IgnoreList;
import com.aerofs.daemon.core.phy.IPhysicalObject;
import static com.aerofs.daemon.core.phy.PhysicalOp.MAP;

import com.aerofs.daemon.core.phy.linked.linker.LinkerRootMap;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.core.store.StoreHierarchy;
import com.aerofs.daemon.lib.db.IMetaDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.lib.cfg.CfgAbsRoots;
import com.aerofs.lib.cfg.CfgStoragePolicy;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.lib.id.*;
import com.aerofs.lib.injectable.InjectableDriver;
import com.aerofs.lib.injectable.InjectableDriver.FIDAndType;
import com.aerofs.lib.injectable.InjectableFile;
import com.aerofs.lib.os.IOSUtil;
import com.aerofs.testlib.AbstractTest;
import com.google.common.collect.ImmutableList;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import java.io.IOException;
import java.sql.SQLException;

import static org.mockito.Mockito.*;

public abstract class AbstractTestLinkedObject<T extends IPhysicalObject> extends AbstractTest
{
    @Mock private DirectoryService ds;
    @Mock private InjectableDriver dr;
    @Mock private Trans t;
    @Mock private LinkerRootMap lrm;
    @Mock private InjectableFile.Factory factFile;
    @Mock private IgnoreList il;
    @Mock private OA oa;
    @Mock private IOSUtil os;
    @Mock private IMetaDatabase mdb;
    @Mock private NRODatabase nrodb;
    @InjectMocks private RepresentabilityHelper rh;

    LinkedStorage s;

    static final SID rootSID = SID.generate();

    SIndex sidx = new SIndex(1);
    SOID parent = new SOID(sidx, OID.generate());
    SOID soid = new SOID(sidx, OID.generate());
    SOKID sokid = new SOKID(soid, KIndex.MASTER);
    ResolvedPath path = new ResolvedPath(rootSID,
            ImmutableList.of(parent, soid),
            ImmutableList.of("foo", "bar"));
    LinkedPath lp = LinkedPath.representable(path, "/ROOT");
    private final FID fid = new FID(new byte[0]);

    private T obj;

    /**
     * @return the specialized physical object under test
     */
    protected abstract T createPhysicalObject(LinkedStorage s, SOKID sokid, LinkedPath path)
            throws SQLException;

    protected abstract void move(IPhysicalObject obj,
            ResolvedPath path, SOKID sokid, PhysicalOp op, Trans t)
            throws IOException, SQLException;

    @Before
    public void setupAbstractTestLocalObject() throws IOException, SQLException, ExNotFound
    {
        when(dr.getFIDAndTypeNullable(any(String.class))).thenReturn(new FIDAndType(fid, false));

        when(lrm.absRootAnchor_(rootSID)).thenReturn("");
        when(factFile.create(any(String.class))).then(RETURNS_MOCKS);

        when(ds.getOA_(soid)).thenReturn(oa);
        when(ds.getOANullable_(soid)).thenReturn(oa);
        when(oa.fid()).thenReturn(fid);

        when(nrodb.getConflicts_(any(SOID.class))).thenReturn(new IDBIterator<SOID>() {
            @Override
            public SOID get_() throws SQLException
            {
                return null;
            }

            @Override
            public boolean next_() throws SQLException
            {
                return false;
            }

            @Override
            public void close_() throws SQLException
            {
            }

            @Override
            public boolean closed_()
            {
                throw new UnsupportedOperationException();
            }
        });

        s = new LinkedStorage(factFile, new IFIDMaintainer.Factory(dr, ds), lrm,
                mock(IOSUtil.class), mock(InjectableDriver.class), rh,
                mock(StoreHierarchy.class), mock(IMapSIndex2SID.class), mock(CfgAbsRoots.class),
                mock(CfgStoragePolicy.class), il, mock(SharedFolderTagFileAndIcon.class),
                mock(LinkedStagingArea.class), mock(LinkedRevProvider.class),
                mock(HashQueue.class), mock(CoreScheduler.class));

        obj = createPhysicalObject(s, sokid, lp);
    }

    @Test
    public void shouldSetFIDOnCreation() throws IOException, SQLException
    {
        // we set op to MAP for all the test cases in this class, as the test subject is much more
        // likely to forget to maintain FID in this case.
        obj.create_(MAP, t);

        verify(ds).setFID_(soid, fid, t);
    }

    @Test
    public void shouldResetFIDOnDeletion() throws IOException, SQLException
    {
        obj.delete_(MAP, t);

        verify(ds).unsetFID_(soid, t);
    }

    @Test
    public void shouldNotUpdateFIDWhenMovingTheSameObject() throws IOException, SQLException
    {
        move(obj, path, sokid, MAP, t);

        verify(ds, never()).setFID_(any(SOID.class), any(FID.class), any(Trans.class));
    }

    @Test
    public void shouldUpdateFIDWhenMovingToDifferentObject() throws IOException, SQLException
    {
        SOID soid2 = new SOID(soid.sidx(), OID.generate());
        ResolvedPath path2 = new ResolvedPath(rootSID,
                ImmutableList.of(parent, soid2),
                ImmutableList.of("foo", "baz"));

        move(obj, path2, new SOKID(soid2, KIndex.MASTER), MAP, t);

        verify(ds).unsetFID_(soid, t);
        verify(ds).setFID_(soid2, fid, t);
    }
}
