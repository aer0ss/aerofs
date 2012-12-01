/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server;

import com.aerofs.lib.FullName;
import com.aerofs.lib.SecUtil;
import com.aerofs.lib.async.UncancellableFuture;
import com.aerofs.lib.ex.ExAlreadyExist;
import com.aerofs.lib.id.DID;
import com.aerofs.lib.id.UserID;
import com.aerofs.servlets.MockSessionUser;
import com.aerofs.servlets.lib.db.LocalTestDatabaseConfigurator;
import com.aerofs.servlets.lib.db.SPDatabaseParams;
import com.aerofs.servlets.lib.db.SQLThreadLocalTransaction;
import com.aerofs.sp.server.lib.SPDatabase;
import com.aerofs.sp.server.lib.cert.Certificate;
import com.aerofs.sp.server.lib.cert.CertificateDatabase;
import com.aerofs.sp.server.lib.cert.CertificateGenerator;
import com.aerofs.sp.server.lib.device.Device;
import com.aerofs.sp.server.lib.device.DeviceDatabase;
import com.aerofs.sp.server.lib.OrganizationDatabase;
import com.aerofs.sp.server.lib.UserDatabase;
import com.aerofs.sp.server.lib.organization.OrgID;
import com.aerofs.sp.server.lib.organization.Organization;
import com.aerofs.sp.server.lib.user.AuthorizationLevel;
import com.aerofs.sp.server.lib.user.User;
import com.aerofs.proto.Common.Void;
import com.aerofs.sp.server.email.InvitationEmailer;
import com.aerofs.sp.server.organization.OrganizationManagement;
import com.aerofs.sp.server.user.UserManagement;
import com.aerofs.testlib.AbstractTest;
import com.aerofs.verkehr.client.lib.admin.VerkehrAdmin;
import com.aerofs.verkehr.client.lib.publisher.VerkehrPublisher;
import org.junit.After;
import org.junit.Before;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import sun.security.pkcs.PKCS10;

import java.sql.SQLException;
import java.sql.Timestamp;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A base class for all tests using the SPService as the "seam"
 */
public class AbstractSPServiceTest extends AbstractTest
{
    // Some subclasses will add custom mocking to the verkehr objects.
    @Mock protected VerkehrPublisher verkehrPublisher;
    @Mock protected VerkehrAdmin verkehrAdmin;

    // Inject a real (spy) local test SP database into the SPService of AbstractSPServiceTest.
    protected final SPDatabaseParams dbParams = new SPDatabaseParams();
    @Spy protected final SQLThreadLocalTransaction transaction =
            new SQLThreadLocalTransaction(dbParams.getProvider());
    @Spy protected SPDatabase db = new SPDatabase(transaction);
    @Spy protected DeviceDatabase ddb = new DeviceDatabase(transaction);
    @Spy protected UserDatabase udb = new UserDatabase(transaction);
    @Spy protected CertificateDatabase certdb = new CertificateDatabase(transaction);
    @Spy protected OrganizationDatabase odb = new OrganizationDatabase(transaction);

    protected CertificateGenerator certgen = mock(CertificateGenerator.class);
    @Spy protected Organization.Factory factOrg = new Organization.Factory(odb);
    @Spy protected User.Factory factUser = new User.Factory(udb, factOrg);
    @Spy protected Device.Factory factDevice = new Device.Factory(ddb, factUser, certdb, certgen);

    // To simulate service.signIn(USER, PASSWORD), subclasses can call setSessionUser(UserID)
    @Spy protected MockSessionUser sessionUser;

    // Mock invitation emailer for use with sp.shareFolder calls
    protected final InvitationEmailer.Factory factEmailer = mock(InvitationEmailer.Factory.class);

    @Spy UserManagement userManagement = new UserManagement(db, factUser, factEmailer, null);
    @Spy OrganizationManagement organizationManagement =
            new OrganizationManagement(db);
    @Spy SharedFolderManagement sharedFolderManagement = new SharedFolderManagement(db,
            userManagement, factEmailer, factUser, factOrg);

    // Subclasses can declare a @Mock'd or @Spy'd object for
    // - UserManagement,
    // - InvitationEmailer, or
    // - LocalTestSPDatabase
    // N.B. the @Mock is only necessary if the subclass will mock the object in some special way
    @InjectMocks protected SPService service;

    protected static final UserID TEST_USER_1 = UserID.fromInternal("user_1");
    protected static final byte[] TEST_USER_1_CRED = "CREDENTIALS".getBytes();

    protected static final UserID TEST_USER_2 = UserID.fromInternal("user_2");
    protected static final byte[] TEST_USER_2_CRED = "CREDENTIALS".getBytes();

    protected static final UserID TEST_USER_3 = UserID.fromInternal("user_3");
    protected static final byte[] TEST_USER_3_CRED = "CREDENTIALS".getBytes();

    public static void addTestUser(UserDatabase udb, UserID userId)
            throws ExAlreadyExist, SQLException
    {
        udb.addUser(userId, new FullName("first", "last"), SecUtil.newRandomBytes(10),
                OrgID.DEFAULT, AuthorizationLevel.USER);
    }

    protected void addTestUser(UserID userId)
            throws ExAlreadyExist, SQLException
    {
        addTestUser(udb, userId);
    }

    // User based tests will probably need to mock verkehr publishes, so include this utility here.
    protected void setupMockVerkehrToSuccessfullyPublish()
    {
        when(verkehrPublisher.publish_(any(String.class), any(byte[].class)))
                .thenReturn(UncancellableFuture.<Void>createSucceeded(null));
    }

    @After
    public void tearDownAbstractSPServiceTest()
            throws SQLException
    {
        if (transaction.isInTransaction()) transaction.rollback();
        transaction.cleanUp();
    }

    // Use a method name that is unlikely to conflict with setup methods in subclasses
    @Before
    public void setupAbstractSPServiceTest()
            throws Exception
    {
        // Database setup.
        LocalTestDatabaseConfigurator.initializeLocalDatabase(dbParams);

        // Verkehr setup.
        service.setVerkehrClients_(verkehrPublisher, verkehrAdmin);

        // return stub invitation emails to avoid NPE
        when(factEmailer.createUserInvitation(anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString())).thenReturn(new InvitationEmailer());
        when(factEmailer.createFolderInvitation(anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString())).thenReturn(new InvitationEmailer());

        OrgID orgId = OrgID.DEFAULT;
        AuthorizationLevel level = AuthorizationLevel.USER;

        // Add all the users to the db.
        transaction.begin();
        udb.addUser(TEST_USER_1, new FullName(TEST_USER_1.toString(), TEST_USER_1.toString()),
                TEST_USER_1_CRED, orgId, level);
        udb.setVerified(TEST_USER_1);
        udb.addUser(TEST_USER_2, new FullName(TEST_USER_2.toString(), TEST_USER_2.toString()),
                TEST_USER_2_CRED, orgId, level);
        udb.setVerified(TEST_USER_2);
        udb.addUser(TEST_USER_3, new FullName(TEST_USER_3.toString(), TEST_USER_3.toString()),
                TEST_USER_3_CRED, orgId, level);
        udb.setVerified(TEST_USER_3);
        transaction.commit();
    }

    protected void setSessionUser(UserID userId)
    {
        sessionUser.set(factUser.create(userId));
    }

    protected void mockCertificateGeneratorAndIncrementSerialNumber() throws Exception
    {
        Certificate cert = mock(Certificate.class);

        // Just stub out the certificate generator. Make sure it doesn't try to contact the CA.
        when(certgen.createCertificate(any(UserID.class), any(DID.class),
                any(PKCS10.class))).thenReturn(cert);

        when(cert.toString()).thenReturn(AbstractSPCertificateBasedTest.RETURNED_CERT);
        when(cert.getSerial()).thenReturn(++AbstractSPCertificateBasedTest._lastSerialNumber);

        // Just need some time in the future - say, one year.
        when(cert.getExpiry()).thenReturn(new Timestamp(System.currentTimeMillis() +
                1000L*60L*60L*24L*365L));
    }
}
