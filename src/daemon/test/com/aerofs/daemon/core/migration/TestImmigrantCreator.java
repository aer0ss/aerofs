package com.aerofs.daemon.core.migration;

import com.aerofs.base.id.OID;
import com.aerofs.base.id.SID;
import com.aerofs.base.id.UniqueID;
import com.aerofs.base.id.UserID;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ds.ResolvedPath;
import com.aerofs.daemon.core.migration.ImmigrantCreator;
import com.aerofs.daemon.core.mock.logical.LogicalObjectsPrinter;
import com.aerofs.daemon.core.mock.logical.MockDS;
import com.aerofs.daemon.core.object.ObjectCreator;
import com.aerofs.daemon.core.object.ObjectDeleter;
import com.aerofs.daemon.core.object.ObjectMover;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.id.SOID;
import com.aerofs.testlib.AbstractTest;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.verify;

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

    @InjectMocks ImmigrantCreator imc;

    MockDS mds;

    SID rootSID = SID.rootSID(UserID.fromInternal("foo"));

    SOID soidFromRoot;
    SOID soidToRootParent;
    SOID soidToRoot;
    String toRootName = "Foo";
    PhysicalOp op = PhysicalOp.APPLY;

    @Before
    public void setup() throws Exception
    {
        mds = new MockDS(rootSID, ds);
    }

    private void setupMockDS(int branches) throws Exception
    {
        soidFromRoot = mds.root().file("from", branches).soid();
        soidToRootParent = mds.root().anchor("to").root().soid();
        soidToRoot = new SOID(soidToRootParent.sidx(), soidFromRoot.oid());

    }

    ////////
    // enforcement tests

    @Test(expected = IllegalArgumentException.class)
    public void shouldAssertDifferentStores() throws Exception
    {
        setupMockDS(0);
        SOID soidToRootParent = new SOID(soidFromRoot.sidx(),
                new OID(UniqueID.generate()));
        imc.createImmigrantRecursively_(ResolvedPath.root(SID.generate()),
                soidFromRoot, soidToRootParent,
                toRootName, op, t);
    }

    @Test(expected = IllegalStateException.class)
    public void shouldAssertSourceAndTargetAreOfSameObjectType() throws Exception
    {
        setupMockDS(0);
        mockTarget(true, false, false);
        initiateMigration();
    }

    @Test(expected = IllegalStateException.class)
    public void shouldAssertAtMostOneObjectIsAdmitted() throws Exception
    {
        setupMockDS(0);
        mockTarget(false, false);
        initiateMigration();
    }

    ////////
    // logic tests

    @Test
    public void shouldMigratePresentSourceToExpelledTarget() throws Exception
    {
        setupMockDS(2);
        mockTarget(true, false);
        shouldInitiateMigration(false);
    }

    @Test
    public void shouldMigratePresentSourceToNonExistingTarget() throws Exception
    {
        setupMockDS(2);
        shouldInitiateMigration(true);
    }

    @Test
    public void shouldMigrateAdmittedAndAbsentSourceToExpelledTarget()
            throws Exception
    {
        setupMockDS(0);
        mockTarget(true, false);
        shouldInitiateMigration(false);
    }

    @Test
    public void shouldMigrateAdmittedAndAbsentSourceToNonExistingTarget()
            throws Exception
    {
        setupMockDS(0);
        shouldInitiateMigration(true);
    }

    @Test
    public void shouldMigrateExpelledSourceToPresentTarget()
            throws Exception
    {
        setupMockDS(-1);
        mockTarget(false, true);
        shouldInitiateMigration(false);
    }

    @Test
    public void shouldMigrateExpelledSourceToAdmittedAndAbsentTarget()
            throws Exception
    {
        setupMockDS(-1);
        mockTarget(false, false);
        shouldInitiateMigration(false);
    }

    @Test
    public void shouldMigrateExpelledSourceToExpelledTarget()
            throws Exception
    {
        setupMockDS(-1);
        mockTarget(true, false);
        shouldInitiateMigration(false);
    }

    @Test
    public void shouldMigrateExpelledSourceToNonExistingTarget()
            throws Exception
    {
        setupMockDS(-1);
        shouldInitiateMigration(true);
    }

    private void mockTarget(boolean expelled, boolean present)
            throws Exception
    {
        mockTarget(expelled, present, true);
    }

    private void mockTarget(boolean expelled, boolean present, boolean sameType)
            throws Exception
    {
        if (expelled) assertFalse(present);

        if (sameType) {
            mds.root().cd("to").file(soidToRoot, soidToRoot.toString(), expelled ? -1 : (present ? 2 : 0));
        } else {
            mds.root().cd("to").dir(soidToRoot, soidToRoot.toString(), expelled);
        }
    }

    private void shouldInitiateMigration(boolean createTarget) throws Exception
    {
        initiateMigration();
        if (createTarget) {
            OA from = ds.getOA_(soidFromRoot);
            verify(oc).createImmigrantMeta_(from.type(), soidFromRoot, soidToRoot,
                    soidToRootParent.oid(), toRootName, op, true, t);
        } else {
            verify(om).moveInSameStore_(soidToRoot, soidToRootParent.oid(), toRootName,
                    op, true, t);
        }
    }

    private void initiateMigration() throws Exception
    {
        LogicalObjectsPrinter.printRecursively(rootSID, ds);
        l.info("{} {}", soidFromRoot, ds.getOANullable_(soidFromRoot));
        imc.createImmigrantRecursively_(ds.resolve_(soidFromRoot).parent(),
                soidFromRoot, soidToRootParent, toRootName, op, t);
    }
}
