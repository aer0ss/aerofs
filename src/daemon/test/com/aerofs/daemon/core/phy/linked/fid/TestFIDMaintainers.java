package com.aerofs.daemon.core.phy.linked.fid;

import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.lib.id.FID;
import com.aerofs.base.id.OID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.aerofs.base.id.UniqueID;
import com.aerofs.lib.injectable.InjectableDriver;
import com.aerofs.lib.injectable.InjectableDriver.FIDAndType;
import com.aerofs.lib.injectable.InjectableFile;
import com.aerofs.testlib.AbstractTest;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.io.IOException;
import java.sql.SQLException;

import static org.mockito.AdditionalMatchers.not;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class TestFIDMaintainers extends AbstractTest
{
    @Mock DirectoryService ds;
    @Mock InjectableDriver dr;
    @Mock InjectableFile f;
    @Mock Trans t;

    @Mock OA oa;

    SOID soid1 = new SOID(new SIndex(1), new OID(UniqueID.generate()));
    SOID soid2 = new SOID(new SIndex(4), new OID(UniqueID.generate()));
    FID fid = new FID(new byte[] {0, 1, 2, 3, 4});

    @Before
    public void setup() throws IOException, SQLException, ExNotFound
    {
        when(dr.getFIDAndType(anyString())).thenReturn(new FIDAndType(fid, false));
        when(ds.getOA_(any(SOID.class))).thenReturn(oa);
        when(oa.fid()).thenReturn(fid);
    }

    @Test
    public void shouldBeNoopWhenMovingMastersUnderSameSOID() throws IOException, SQLException
    {
        IFIDMaintainer from = newMasterFIDMaintainer(soid1);
        IFIDMaintainer to = newMasterFIDMaintainer(soid1);

        from.physicalObjectMoved_(to, t);

        verifyZeroInteractions(ds);
        verifyZeroInteractions(dr);
        verifyZeroInteractions(f);
    }

    @Test
    public void shouldUnsetAndSetFIDWhenMovingMastersUnderDifferentSOID()
            throws IOException, SQLException
    {
        IFIDMaintainer from = newMasterFIDMaintainer(soid1);
        IFIDMaintainer to = newMasterFIDMaintainer(soid2);

        from.physicalObjectMoved_(to, t);

        verify(ds).unsetFID_(soid1, t);
        verify(ds).setFID_(soid2, fid, t);
    }

    @Test
    public void shouldUnsetFIDWhenMovingMasterToNonMaster()
            throws IOException, SQLException
    {
        IFIDMaintainer from = newMasterFIDMaintainer(soid1);
        IFIDMaintainer to = new NonMasterFIDMaintainer();

        from.physicalObjectMoved_(to, t);

        verify(ds).unsetFID_(soid1, t);
    }

    @Test
    public void shouldSetFIDWhenMovingNonMasterToMaster()
            throws IOException, SQLException
    {
        IFIDMaintainer from = new NonMasterFIDMaintainer();
        IFIDMaintainer to = newMasterFIDMaintainer(soid2);

        from.physicalObjectMoved_(to, t);

        verify(ds).setFID_(soid2, fid, t);
    }

    @Test
    public void shouldBeNoopWhenMovingNonMasters()
            throws IOException, SQLException
    {
        IFIDMaintainer from = new NonMasterFIDMaintainer();
        IFIDMaintainer to = new NonMasterFIDMaintainer();

        from.physicalObjectMoved_(to, t);

        verifyZeroInteractions(ds);
        verifyZeroInteractions(dr);
        verifyZeroInteractions(f);
    }

    @Test
    public void shouldUpdateFIDOnCreation() throws Exception
    {
        IFIDMaintainer fidm = newMasterFIDMaintainer(soid1);

        fidm.physicalObjectCreated_(t);

        verify(ds).setFID_(soid1, fid, t);
        verify(ds, never()).setFID_(not(eq(soid1)), any(FID.class), eq(t));
    }

    @Test
    public void shouldRandomizeFIDOnCreationConflict() throws Exception
    {
        IFIDMaintainer fidm = newMasterFIDMaintainer(soid1);
        when(ds.getSOIDNullable_(fid)).thenReturn(soid2);

        fidm.physicalObjectCreated_(t);

        verify(ds).setFID_(eq(soid2), not(eq(fid)), eq(t));
        verify(ds).setFID_(soid1, fid, t);
    }

    private IFIDMaintainer newMasterFIDMaintainer(SOID soid)
    {
        return new MasterFIDMaintainer(ds, dr, f, soid);
    }
}
