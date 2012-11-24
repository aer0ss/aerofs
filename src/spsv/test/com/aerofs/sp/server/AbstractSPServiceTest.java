package com.aerofs.sp.server;

import com.aerofs.servlets.lib.db.SPDatabaseParams;
import com.aerofs.servlets.lib.db.LocalTestDatabaseConfigurator;
import com.aerofs.servlets.lib.db.SQLThreadLocalTransaction;
import com.aerofs.sp.server.lib.SPDatabase;
import com.aerofs.testlib.AbstractTest;
import com.aerofs.verkehr.client.lib.admin.VerkehrAdmin;
import com.aerofs.verkehr.client.lib.publisher.VerkehrPublisher;
import org.junit.After;
import org.junit.Before;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

import java.io.IOException;
import java.sql.SQLException;
/**
 * A base class for all tests using the SPService as the "seam"
 */
public abstract class AbstractSPServiceTest extends AbstractTest
{
    // Some subclasses will add custom mocking to the verkehr objects.
    @Mock protected VerkehrPublisher verkehrPublisher;
    @Mock protected VerkehrAdmin verkehrAdmin;

    // Subclasses can declare a @Mock'd or @Spy'd object for
    // - UserManagement,
    // - InvitationEmailer, or
    // - LocalTestSPDatabase
    // N.B. the @Mock is only necessary if the subclass will mock the object in some special way
    @InjectMocks protected SPService service;

    // Inject a real (spy) local test SP database into the SPService of AbstractSPServiceTest.
   protected final SPDatabaseParams dbParams = new SPDatabaseParams();
    @Spy protected final SQLThreadLocalTransaction transaction =
            new SQLThreadLocalTransaction(dbParams.getProvider());
    @Spy protected SPDatabase db = new SPDatabase(transaction);

    @Before
    public void setupAbstractSPServiceTest()
            throws SQLException, ClassNotFoundException, IOException, InterruptedException
    {
        // Database setup.
        LocalTestDatabaseConfigurator.initializeLocalDatabase(dbParams);

        // Verkehr setup.
        service.setVerkehrClients_(verkehrPublisher, verkehrAdmin);
    }

    @After
    public void tearDownAbstractSPServiceTest()
            throws SQLException
    {
        transaction.cleanUp();
    }
}
