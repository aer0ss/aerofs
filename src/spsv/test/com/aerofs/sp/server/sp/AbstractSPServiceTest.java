package com.aerofs.sp.server.sp;

import com.aerofs.testlib.AbstractTest;
import com.aerofs.verkehr.client.commander.VerkehrCommander;
import com.aerofs.verkehr.client.publisher.VerkehrPublisher;
import org.junit.Before;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

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

    @Before
    public void setupVerkehrClients()
    {
        service.setVerkehrClients_(verkehrPublisher, verkehrCommander);
    }
}
