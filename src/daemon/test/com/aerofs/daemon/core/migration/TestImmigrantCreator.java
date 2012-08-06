package com.aerofs.daemon.core.migration;

import com.aerofs.daemon.core.ds.DirectoryService.IObjectWalker;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ds.OA.Type;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.lib.id.OID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.id.UniqueID;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.object.ObjectCreator;
import com.aerofs.daemon.core.object.ObjectDeleter;
import com.aerofs.daemon.core.object.ObjectMover;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.testlib.AbstractTest;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.sql.SQLException;

import static com.aerofs.daemon.core.mock.TestUtilCore.*;
import static org.junit.Assert.assertFalse;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

/**
 * This class tests ImmigrantDetector.initiateImmigrationRecursively_()
 */
public class TestImmigrantCreator extends AbstractTest
{
    @Mock DirectoryService ds;
    @Mock IMapSIndex2SID sidx2sid;
    @Mock ObjectDeleter od;
    @Mock ObjectCreator oc;
    @Mock ObjectMover om;

    @Mock Trans t;
    @Mock OA oaFromRoot;

    @InjectMocks ImmigrantCreator imc;

    SOID soidFromRoot = new SOID(new SIndex(1), new OID(UniqueID.generate()));
    SOID soidToRootParent = new SOID(new SIndex(2), new OID(UniqueID.generate()));
    SOID soidToRoot = new SOID(soidToRootParent.sidx(), soidFromRoot.oid());
    String toRootName = "Foo";
    PhysicalOp op = PhysicalOp.APPLY;

    @SuppressWarnings("unchecked")
    @Before
    public void setup() throws Exception
    {
        mockOA(oaFromRoot, soidFromRoot, Type.FILE, false, null, null, ds);

        doAnswer(new Answer<Void>()
        {
            @Override
            public Void answer(InvocationOnMock invocationOnMock)
                    throws Throwable
            {
                IObjectWalker<SOID> w = (IObjectWalker<SOID>) invocationOnMock.getArguments()[2];
                w.prefixWalk_(soidToRootParent, oaFromRoot);
                w.postfixWalk_(soidToRootParent, oaFromRoot);
                return null;
            }
        }).when(ds).walk_(eq(soidFromRoot), eq(soidToRootParent),
                any(IObjectWalker.class));
    }

    ////////
    // enforcement tests

    @Test(expected = AssertionError.class)
    public void shouldAssertDifferentStores() throws Exception
    {
        SOID soidToRootParent = new SOID(soidFromRoot.sidx(),
                new OID(UniqueID.generate()));
        imc.createImmigrantRecursively_(soidFromRoot, soidToRootParent, toRootName, op, t);
    }

    @Test(expected = AssertionError.class)
    public void shouldAssertSourceAndTargetAreOfSameObjectType() throws Exception
    {
        mockTarget(true, false, false);
        initiateMigration();
    }

    @Test(expected = AssertionError.class)
    public void shouldAssertAtMostOneObjectIsAdmitted() throws Exception
    {
        mockTarget(false, false);
        initiateMigration();
    }

    ////////
    // logic tests

    @Test
    public void shouldMigratePresentSourceToExpelledTarget() throws Exception
    {
        mockBranches(oaFromRoot, 2, 0, 0, null);
        mockTarget(true, false);
        shouldInitiateMigration(false);
    }

    @Test
    public void shouldMigratePresentSourceToNonExistingTarget() throws Exception
    {
        mockBranches(oaFromRoot, 2, 0, 0, null);
        shouldInitiateMigration(true);
    }

    @Test
    public void shouldMigrateAdmittedAndAbsentSourceToExpelledTarget()
            throws Exception
    {
        // not mocking branches makes the object absent
        mockTarget(true, false);
        shouldInitiateMigration(false);
    }

    @Test
    public void shouldMigrateAdmittedAndAbsentSourceToNonExistingTarget()
            throws Exception
    {
        // not mocking branches makes the object absent
        shouldInitiateMigration(true);
    }

    @Test
    public void shouldMigrateExpelledSourceToPresentTarget()
            throws Exception
    {
        mockTarget(false, true);
        mockOA(oaFromRoot, soidFromRoot, Type.FILE, true, null, null, ds);
        shouldInitiateMigration(false);
    }

    @Test
    public void shouldMigrateExpelledSourceToAdmittedAndAbsentTarget()
            throws Exception
    {
        mockTarget(false, false);
        mockOA(oaFromRoot, soidFromRoot, Type.FILE, true, null, null, ds);
        shouldInitiateMigration(false);
    }

    @Test
    public void shouldMigrateExpelledSourceToExpelledTarget()
            throws Exception
    {
        mockTarget(true, false);
        mockOA(oaFromRoot, soidFromRoot, Type.FILE, true, null, null, ds);
        shouldInitiateMigration(false);
    }

    @Test
    public void shouldMigrateExpelledSourceToNonExistingTarget()
            throws Exception
    {
        mockOA(oaFromRoot, soidFromRoot, Type.FILE, true, null, null, ds);
        shouldInitiateMigration(true);
    }

    private void mockTarget(boolean expelled, boolean present)
            throws SQLException, ExNotFound
    {
        mockTarget(expelled, present, true);
    }

    private void mockTarget(boolean expelled, boolean present, boolean sameType)
            throws SQLException, ExNotFound
    {
        if (expelled) assertFalse(present);
        OA oaToRoot = mock(OA.class);

        mockOA(oaToRoot, soidToRoot, sameType ? Type.FILE : Type.DIR, expelled, null, null, ds);
        if (present) mockBranches(oaToRoot, 2, 0, 0, null);

        when(ds.getOANullable_(soidToRoot)).thenReturn(oaToRoot);
    }

    private void shouldInitiateMigration(boolean createTarget) throws Exception
    {
        initiateMigration();
        if (createTarget) {
            verify(oc).createMeta_(oaFromRoot.type(), soidToRoot, soidToRootParent.oid(),
                    toRootName, oaFromRoot.flags(), op, true, true, t);
        } else {
            verify(om).moveInSameStore_(soidToRoot, soidToRootParent.oid(), toRootName,
                    op, false, true, t);
        }
    }

    private void initiateMigration() throws Exception
    {
        imc.createImmigrantRecursively_(soidFromRoot, soidToRootParent, toRootName, op, t);
    }
}
