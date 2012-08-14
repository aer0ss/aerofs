package com.aerofs.daemon.core.linker;

import com.aerofs.daemon.core.VersionUpdater;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.fs.HdMoveObject;
import com.aerofs.daemon.core.linker.MightCreate.Result;
import com.aerofs.daemon.core.mock.logical.MockDir;
import com.aerofs.daemon.core.mock.logical.MockFile;
import com.aerofs.daemon.core.mock.logical.MockRoot;
import com.aerofs.daemon.core.mock.physical.MockPhysicalDir;
import com.aerofs.daemon.core.mock.physical.MockPhysicalFile;
import com.aerofs.daemon.core.object.ObjectCreator;
import com.aerofs.daemon.core.object.ObjectMover;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.Util;
import com.aerofs.lib.analytics.Analytics;
import com.aerofs.lib.cfg.CfgAbsRootAnchor;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.aerofs.lib.id.FID;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.Path;
import com.aerofs.lib.injectable.InjectableDriver;
import com.aerofs.lib.injectable.InjectableFile;
import com.aerofs.testlib.AbstractTest;

import javax.annotation.Nullable;
import org.junit.Before;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.IOException;
import java.sql.SQLException;

import static org.mockito.Mockito.*;

/**
 * Subclasses of this class test MightCreate. Each subclass tests a certain combination
 * of relations between a logical file and a physical file. For example,
 * {@code TestMightCreate_DiffFIDSamePathDiffType} tests the case where a physical file has a
 * different FID, the same path, and a different file type (file vs directory) with a logical file.
 * Because MightCreate's implementation is modeled after these combinations, we test it with
 * the same model.
 */
public abstract class AbstractTestMightCreate extends AbstractTest
{
    @Mock IgnoreList il;
    @Mock DirectoryService ds;
    @Mock HdMoveObject hdmo;
    @Mock ObjectMover om;
    @Mock ObjectCreator oc;
    @Mock CfgLocalUser cfgUser;
    @Mock VersionUpdater vu;
    @Mock InjectableFile.Factory factFile;
    @Mock InjectableDriver dr;
    @Mock CfgAbsRootAnchor cfgAbsRootAnchor;
    @Mock IDeletionBuffer delBuffer;
    @Mock Trans t;
    @Mock Analytics a;

    @InjectMocks MightCreate mc;

    MockPhysicalDir osRoot =
        new MockPhysicalDir("root",
            new MockPhysicalFile("f1"),
            new MockPhysicalFile("F1"),
            new MockPhysicalFile("f2"),
            new MockPhysicalFile("f2 (3)"),
            new MockPhysicalDir("d3"),
            new MockPhysicalDir("d4"),
            new MockPhysicalFile("ignored"),
            new MockPhysicalFile("f5")
        );

    MockRoot logicRoot =
        new MockRoot(
            new MockFile("f1", 2),
            new MockDir("f2"),
            new MockDir("f2 (2)"),
            new MockDir("d4"),
            new MockFile("f5", 0)   // a file with no master branch
        );

    static String pRoot = Util.join("root");

    @Before
    public void setupAbstractClass() throws Exception
    {
        osRoot.mock(factFile, dr);
        logicRoot.mock(ds, null, null, null, null, null);

        when(cfgAbsRootAnchor.get()).thenReturn(pRoot);

        when(il.isIgnored_("ignored")).thenReturn(true);

        when(hdmo.move_(any(SOID.class), any(SOID.class), any(String.class), any(PhysicalOp.class),
                any(Trans.class))).then(new Answer<SOID>() {
                    @Override
                    public SOID answer(InvocationOnMock invocation)
                            throws Throwable
                    {
                        return (SOID) invocation.getArguments()[0];
                    }
                });
    }

    /**
     * assign the FID to the object specified by the SOID
     */
    protected void assign(SOID soid, FID fid) throws IOException, SQLException
    {
        OA oa = ds.getOANullable_(soid);
        when(ds.getSOID_(fid)).thenReturn(soid);
        when(oa.fid()).thenReturn(fid);
    }

    /**
     * @param logicalObjRemovedFromDelBuf the name of the logical object which should be removed
     * from the deletion buffer after the mightCreate call. Set to null to verify that no object
     * has been removed from the deletion buffer.
     */
    protected Result mightCreate(String physicalObj, @Nullable String logicalObjRemovedFromDelBuf)
            throws Exception
    {
        Result res = mc.mightCreate_(new PathCombo(cfgAbsRootAnchor, Util.join(pRoot, physicalObj)),
                delBuffer, t);

        if (logicalObjRemovedFromDelBuf != null) {
            verify(delBuffer).remove_(ds.resolveNullable_(new Path(logicalObjRemovedFromDelBuf)));
        } else {
            verify(delBuffer, never()).remove_(any(SOID.class));
        }

        return res;
    }
}
