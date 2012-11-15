package com.aerofs.daemon.core.phy.linked;

import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.linker.IgnoreList;
import com.aerofs.daemon.core.phy.IPhysicalObject;
import static com.aerofs.daemon.core.phy.PhysicalOp.MAP;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.Path;
import com.aerofs.lib.cfg.CfgAbsRootAnchor;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.lib.id.*;
import com.aerofs.lib.injectable.InjectableDriver;
import com.aerofs.lib.injectable.InjectableDriver.FIDAndType;
import com.aerofs.lib.injectable.InjectableFile;
import com.aerofs.lib.injectable.InjectableFile.Factory;
import com.aerofs.testlib.AbstractTest;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import java.io.IOException;
import java.sql.SQLException;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

public abstract class AbstractTestLinkedObject<T extends IPhysicalObject> extends AbstractTest
{
    @Mock private DirectoryService ds;
    @Mock private InjectableDriver dr;
    @Mock private Trans t;
    @Mock private CfgAbsRootAnchor cfgAbsRootAnchor;
    @Mock private InjectableFile.Factory factFile;
    @Mock private IgnoreList il;
    @Mock private OA oa;

    SOID soid = new SOID(new SIndex(1), new OID(UniqueID.generate()));
    SOKID sokid = new SOKID(soid, KIndex.MASTER);
    Path path = new Path("foo", "bar");
    private final FID fid = new FID(new byte[0]);

    private T obj;

    /**
     * @return the specialized physical object under test
     */
    protected abstract T createPhysicalObject(CfgAbsRootAnchor cfgAbsRootAnchor, Factory factFile,
            InjectableDriver dr, DirectoryService ds, IgnoreList il, SOKID sokid, Path path);

    @Before
    public void setupAbstractTestLocalObject() throws IOException, SQLException, ExNotFound
    {
        when(dr.getFIDAndType(any(String.class))).thenReturn(new FIDAndType(fid, false));

        when(cfgAbsRootAnchor.get()).thenReturn("");
        when(factFile.create(any(String.class))).then(RETURNS_MOCKS);

        when(ds.getOA_(soid)).thenReturn(oa);
        when(ds.getOANullable_(soid)).thenReturn(oa);
        when(oa.fid()).thenReturn(fid);

        obj = createPhysicalObject(cfgAbsRootAnchor, factFile, dr, ds, il, sokid, path);
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

        verify(ds).setFID_(soid, null, t);
    }

    @Test
    public void shouldNotUpdateFIDWhenMovingTheSameObject() throws IOException, SQLException
    {
        T obj2 = createPhysicalObject(cfgAbsRootAnchor, factFile, dr, ds, il, sokid, path);

        obj.move_(obj2, MAP, t);

        verify(ds, never()).setFID_(any(SOID.class), any(FID.class), any(Trans.class));
    }

    @Test
    public void shouldUpdateFIDWhenMovingToDifferentObject() throws IOException, SQLException
    {
        SOID soid2 = new SOID(soid.sidx(), new OID(UniqueID.generate()));
        SOKID sokid2 = new SOKID(soid2, KIndex.MASTER);

        T obj2 = createPhysicalObject(cfgAbsRootAnchor, factFile, dr, ds, il, sokid2, path);

        obj.move_(obj2, MAP, t);

        verify(ds).setFID_(soid, null, t);
        verify(ds).setFID_(soid2, fid, t);
    }
}
