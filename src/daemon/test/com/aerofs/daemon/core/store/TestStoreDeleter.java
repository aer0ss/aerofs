package com.aerofs.daemon.core.store;

import static com.aerofs.daemon.core.mock.TestUtilCore.mockBranches;
import static com.aerofs.daemon.core.mock.TestUtilCore.mockOA;
import static com.aerofs.daemon.core.mock.TestUtilCore.mockStore;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashSet;

import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.Path;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ds.OA.Type;
import com.aerofs.daemon.core.mock.TestUtilCore.ExArbitrary;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.lib.id.KIndex;
import com.aerofs.base.id.OID;
import com.aerofs.base.id.SID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.id.SOKID;
import com.aerofs.base.id.UniqueID;
import com.aerofs.testlib.AbstractTest;

public class TestStoreDeleter extends AbstractTest
{
    @Mock DirectoryService ds;
    @Mock IPhysicalStorage ps;
    @Mock IStores ss;
    @Mock IMapSIndex2SID sidx2sid;
    @Mock StoreDeletionOperators _operators;
    @Mock Trans t;

    @InjectMocks StoreDeleter sd;

    // these objects are under the grand child
    @Mock OA oaFile;
    @Mock OA oaFolder;
    @Mock OA oaAnchor;
    @Mock OA oaFolderExpelled;
    @Mock OA oaAnchorExpelled;

    // object names
    String nChild = "c";
    String nGrandChild1 = "gc1";
    String nGrandChild2 = "gc2";
    String nFile = "file";
    String nFolder = "folder";
    String nAnchor = "anchor";
    String nFolderExpelled = "folderEx";
    String nAnchorExpelled = "anchorEx";

    Path pNewRoot = new Path("n1", "n2", "n3");
    Path pNewChild = pNewRoot.append(nChild);
    Path pNewGrandChild = pNewChild.append(nGrandChild1)
            .append(nGrandChild2);

    Path pOldRoot = new Path("1", "2", "3");
    Path pOldChild = pOldRoot.append(nChild);
    Path pOldGrandChild = pOldChild.append(nGrandChild1)
            .append(nGrandChild2);

    Path pOldFile = pOldGrandChild.append(nFile);
    Path pOldFolder = pOldGrandChild.append(nFolder);
    Path pOldAnchor = pOldGrandChild.append(nAnchor);
    Path pOldFolderExpelled = pOldGrandChild.append(nFolderExpelled);
    Path pOldAnchorExpelled = pOldGrandChild.append(nAnchorExpelled);

    SIndex sidxRootParent = new SIndex(4);
    SIndex sidxRoot = new SIndex(3);
    SIndex sidxChild = new SIndex(2);
    SIndex sidxGrandChild = new SIndex(1);
    SID sidRoot = SID.generate();
    SID sidChild = SID.generate();
    SID sidGrandChild = SID.generate();
    SOID soidAnchorChild = new SOID(sidxRoot, SID.storeSID2anchorOID(sidChild));
    SOID soidAnchorGrandChild = new SOID(sidxChild, SID.storeSID2anchorOID(sidGrandChild));

    SOID soidFile = new SOID(sidxGrandChild, new OID(UniqueID.generate()));
    SOID soidFolder = new SOID(sidxGrandChild, new OID(UniqueID.generate()));
    SOID soidAnchor = new SOID(sidxGrandChild, new OID(UniqueID.generate()));
    SOID soidFolderExpelled = new SOID(sidxGrandChild, new OID(UniqueID.generate()));
    SOID soidAnchorExpelled = new SOID(sidxGrandChild, new OID(UniqueID.generate()));

    @Before
    public void setup() throws Exception
    {
        mockStore(null, sidRoot, sidxRoot, sidxRootParent, ss, null, null, sidx2sid);
        mockStore(null, sidChild, sidxChild, sidxRoot, ss, null, null, sidx2sid);
        mockStore(null, sidGrandChild, sidxGrandChild, sidxChild, ss, null, null, sidx2sid);

        mockOA(oaFile, soidFile, Type.FILE, false, null, nFile, ds);
        mockOA(oaFolder, soidFolder, Type.DIR, false, null, nFolder, ds);
        mockOA(oaAnchor, soidAnchor, Type.ANCHOR, false, null, nAnchor, ds);
        mockOA(oaFolderExpelled, soidFolderExpelled, Type.DIR, true, null, nFolderExpelled, ds);
        mockOA(oaAnchorExpelled, soidAnchorExpelled, Type.ANCHOR, true, null, nAnchorExpelled, ds);

        mockBranches(oaFile, 2, 0, 0, null);

        when(ps.newFile_(any(SOKID.class), any(Path.class))).then(RETURNS_MOCKS);
        when(ps.newFolder_(any(SOID.class), any(Path.class))).then(RETURNS_MOCKS);

        OA dummy = mock(OA.class);
        when(ds.getOANullable_(soidAnchorChild)).thenReturn(dummy);
        when(ds.getOANullable_(soidAnchorGrandChild)).thenReturn(dummy);

        mockPathResolution(new SOID(sidxRoot, OID.ROOT), pNewRoot);
        mockPathResolution(new SOID(sidxChild, OID.ROOT), pNewChild);
        mockPathResolution(new SOID(sidxGrandChild, OID.ROOT), pNewGrandChild);
        mockPathResolution(new SOID(sidxRoot, SID.storeSID2anchorOID(sidChild)), pNewChild);
        mockPathResolution(new SOID(sidxChild, SID.storeSID2anchorOID(sidGrandChild)),
                pNewGrandChild);

        HashSet<OID> children = new HashSet<OID>();
        children.add(soidFile.oid());
        children.add(soidFolder.oid());
        children.add(soidAnchor.oid());
        children.add(soidFolderExpelled.oid());
        children.add(soidAnchorExpelled.oid());
        when(ds.getChildren_(new SOID(sidxGrandChild, OID.ROOT))).thenReturn(children);
    }

    private void mockPathResolution(SOID soid, Path path) throws SQLException
    {
        OA dummy = mock(OA.class);
        when(ds.getOANullable_(soid)).thenReturn(dummy);
        when(ds.resolve_(dummy)).thenReturn(path);
    }

    @Test
    public void shouldDeleteSelfAndChildStores() throws Exception
    {
        delete(PhysicalOp.APPLY);

        verifyStoreDeletion(sidxRoot);
        verifyStoreDeletion(sidxChild);
        verifyStoreDeletion(sidxGrandChild);
    }

    @Test
    public void shouldDeletePhysicalObjects() throws Exception
    {
        delete(PhysicalOp.APPLY);

        verify(ps).newFolder_(soidFolder, pOldFolder);
        verify(ps).newFolder_(soidAnchor, pOldAnchor);

        for (KIndex kidx : oaFile.cas().keySet()) {
            SOKID sokid = new SOKID(oaFile.soid(), kidx);
            verify(ps).newFile_(sokid, pOldFile);
        }
    }

    @Test
    public void shouldNotDeleteExpelledAnchorsAndFolders() throws Exception
    {
        delete(PhysicalOp.APPLY);

        verify(ps, never()).newFolder_(oaFolderExpelled.soid(), pOldFolderExpelled);
        verify(ps, never()).newFolder_(oaAnchorExpelled.soid(), pOldAnchorExpelled);
    }

    @Test (expected = ExArbitrary.class)
    public void shouldThrowOnException() throws Exception
    {
        doThrow(new ExArbitrary()).when(ps).deleteStore_(eq(sidxGrandChild), any(Path.class),
                any(PhysicalOp.class), eq(t));

        delete(PhysicalOp.APPLY);
    }

    private void delete(PhysicalOp op) throws Exception
    {
        sd.removeParentStoreReference_(sidxRoot, sidxRootParent, pOldRoot, op, t);
    }

    private void verifyStoreDeletion(SIndex sidx) throws SQLException, IOException
    {
        verify(ps).deleteStore_(eq(sidx), any(Path.class), any(PhysicalOp.class), eq(t));
        verify(_operators).runAll_(sidx, t);
    }
}
