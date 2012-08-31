package com.aerofs.sp.server.sp;

import com.aerofs.servletlib.MockSessionUserID;
import com.aerofs.servletlib.db.SPDatabaseParams;
import com.aerofs.servletlib.db.LocalTestDatabaseConfigurator;
import com.aerofs.servletlib.db.SQLThreadLocalTransaction;
import com.aerofs.servletlib.sp.SPDatabase;
import com.aerofs.testlib.AbstractTest;
import com.aerofs.verkehr.client.lib.commander.VerkehrCommander;
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
    // To mock service.signIn(USER, PASSWORD), subclasses can call
    // sessionUser.set(USER)
    @Spy protected MockSessionUserID sessionUser;

    // Some subclasses will add custom mocking to the verkehr objects.
    @Mock protected VerkehrPublisher verkehrPublisher;
    @Mock protected VerkehrCommander verkehrCommander;

    // Subclasses can declare a @Mock'd or @Spy'd object for
    // - UserManagement,
    // - InvitationEmailer, or
    // - LocalTestSPDatabase
    // N.B. the @Mock is only necessary if the subclass will mock the object in some special way
    @InjectMocks protected SPService service;

    // Inject a real (spy) local test SP database into the SPService of AbstractSPServiceTest.
   protected final SPDatabaseParams _dbParams = new SPDatabaseParams();
    @Spy protected final SQLThreadLocalTransaction _transaction =
            new SQLThreadLocalTransaction(_dbParams.getProvider());
    @Spy protected SPDatabase db = new SPDatabase(_transaction);

    @Before
    public void setupAbstractSPServiceTest()
            throws SQLException, ClassNotFoundException, IOException, InterruptedException
    {
        // Database setup.
        LocalTestDatabaseConfigurator.initializeLocalDatabase(_dbParams);

        // Verkehr setup.
        service.setVerkehrClients_(verkehrPublisher, verkehrCommander);
    }

    @After
    public void tearDownAbstractSPServiceTest()
            throws SQLException
    {
        _transaction.cleanUp();
    }
}
