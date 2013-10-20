package com.aerofs.daemon.core.phy.linked;

import com.aerofs.base.id.OID;
import com.aerofs.base.id.SID;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ds.ResolvedPath;
import com.aerofs.daemon.core.phy.linked.fid.IFIDMaintainer;
import com.aerofs.daemon.core.phy.linked.linker.IgnoreList;
import com.aerofs.daemon.core.phy.IPhysicalObject;
import static com.aerofs.daemon.core.phy.PhysicalOp.MAP;

import com.aerofs.daemon.core.phy.linked.linker.LinkerRootMap;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.core.store.IStores;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.lib.cfg.CfgAbsRoots;
import com.aerofs.lib.cfg.CfgStoragePolicy;
import com.aerofs.lib.id.*;
import com.aerofs.lib.injectable.InjectableDriver;
import com.aerofs.lib.injectable.InjectableDriver.FIDAndType;
import com.aerofs.lib.injectable.InjectableFile;
import com.aerofs.testlib.AbstractTest;
import com.google.common.collect.ImmutableList;
import org.junit.Before;
import org.junit.Test;
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

    LinkedStorage s;

    static final SID rootSID = SID.generate();

    SIndex sidx = new SIndex(1);
    SOID parent = new SOID(sidx, OID.generate());
    SOID soid = new SOID(sidx, OID.generate());
    SOKID sokid = new SOKID(soid, KIndex.MASTER);
    ResolvedPath path = new ResolvedPath(rootSID,
            ImmutableList.of(parent, soid),
            ImmutableList.of("foo", "bar"));
    private final FID fid = new FID(new byte[0]);

    private T obj;

    /**
     * @return the specialized physical object under test
     */
    protected abstract T createPhysicalObject(LinkedStorage s, ResolvedPath path, KIndex kidx);

    @Before
    public void setupAbstractTestLocalObject() throws IOException, SQLException, ExNotFound
    {
        when(dr.getFIDAndType(any(String.class))).thenReturn(new FIDAndType(fid, false));

        when(lrm.absRootAnchor_(rootSID)).thenReturn("");
        when(factFile.create(any(String.class))).then(RETURNS_MOCKS);

        when(ds.getOA_(soid)).thenReturn(oa);
        when(ds.getOANullable_(soid)).thenReturn(oa);
        when(oa.fid()).thenReturn(fid);

        s = new LinkedStorage(factFile, new IFIDMaintainer.Factory(dr, ds), lrm,
                mock(IStores.class), mock(IMapSIndex2SID.class), mock(CfgAbsRoots.class),
                mock(CfgStoragePolicy.class), il, mock(SharedFolderTagFileAndIcon.class));

        obj = createPhysicalObject(s, path, KIndex.MASTER);
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
        T obj2 = createPhysicalObject(s, path, KIndex.MASTER);

        obj.move_(obj2, MAP, t);

        verify(ds, never()).setFID_(any(SOID.class), any(FID.class), any(Trans.class));
    }

    @Test
    public void shouldUpdateFIDWhenMovingToDifferentObject() throws IOException, SQLException
    {
        SOID soid2 = new SOID(soid.sidx(), OID.generate());
        ResolvedPath path2 = new ResolvedPath(rootSID,
                ImmutableList.of(parent, soid2),
                ImmutableList.of("foo", "baz"));

        T obj2 = createPhysicalObject(s, path2, KIndex.MASTER);

        obj.move_(obj2, MAP, t);

        verify(ds).unsetFID_(soid, t);
        verify(ds).setFID_(soid2, fid, t);
    }
}
