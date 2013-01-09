/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.integration;

import com.aerofs.base.id.DID;
import com.aerofs.lib.FullName;
import com.aerofs.lib.SecUtil;
import com.aerofs.base.async.UncancellableFuture;
import com.aerofs.lib.ex.ExAlreadyExist;
import com.aerofs.base.ex.ExBadCredential;
import com.aerofs.base.id.UserID;
import com.aerofs.servlets.MockSessionUser;
import com.aerofs.sp.server.AbstractTestWithSPDatabase;
import com.aerofs.sp.server.PasswordManagement;
import com.aerofs.sp.server.SPService;
import com.aerofs.sp.server.SharedFolderInvitation;
import com.aerofs.sp.server.email.PasswordResetEmailer;
import com.aerofs.sp.server.lib.EmailSubscriptionDatabase;
import com.aerofs.sp.server.lib.OrganizationInvitationDatabase;
import com.aerofs.sp.server.lib.SPDatabase;
import com.aerofs.sp.server.lib.SharedFolder;
import com.aerofs.sp.server.lib.SharedFolderDatabase;
import com.aerofs.sp.server.lib.SharedFolderInvitationDatabase;
import com.aerofs.sp.server.lib.cert.Certificate;
import com.aerofs.sp.server.lib.cert.CertificateDatabase;
import com.aerofs.sp.server.lib.cert.CertificateGenerator;
import com.aerofs.sp.server.lib.cert.CertificateGenerator.CertificateGenerationResult;
import com.aerofs.sp.server.lib.device.Device;
import com.aerofs.sp.server.lib.device.DeviceDatabase;
import com.aerofs.sp.server.lib.OrganizationDatabase;
import com.aerofs.sp.server.lib.UserDatabase;
import com.aerofs.sp.server.lib.organization.OrganizationID;
import com.aerofs.sp.server.lib.organization.Organization;
import com.aerofs.sp.server.lib.organization.OrganizationInvitation;
import com.aerofs.sp.server.lib.session.CertificateAuthenticator;
import com.aerofs.sp.server.lib.user.AuthorizationLevel;
import com.aerofs.sp.server.lib.user.User;
import com.aerofs.proto.Common.Void;
import com.aerofs.sp.server.session.SPActiveTomcatSessionTracker;
import com.aerofs.sp.server.session.SPActiveUserSessionTracker;
import com.aerofs.sp.server.session.SPSessionInvalidator;
import com.aerofs.verkehr.client.lib.admin.VerkehrAdmin;
import com.aerofs.verkehr.client.lib.publisher.VerkehrPublisher;
import com.google.protobuf.ByteString;
import org.junit.Before;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import sun.security.pkcs.PKCS10;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.sql.SQLException;
import java.sql.Timestamp;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * A base class for all tests using the SPService as the "seam"
 */
public class AbstractSPTest extends AbstractTestWithSPDatabase
{
    // Some subclasses will add custom mocking to the verkehr objects.
    @Mock protected VerkehrPublisher verkehrPublisher;
    @Mock protected VerkehrAdmin verkehrAdmin;

    protected SPActiveUserSessionTracker userSessionTracker =
            new SPActiveUserSessionTracker();
    protected SPActiveTomcatSessionTracker tomcatSessionTracker =
            new SPActiveTomcatSessionTracker();
    @Spy protected SPSessionInvalidator sessionInvalidator = new SPSessionInvalidator(
            userSessionTracker, tomcatSessionTracker);

    @Spy protected SPDatabase db = new SPDatabase(trans);
    @Spy protected DeviceDatabase ddb = new DeviceDatabase(trans);
    @Spy protected UserDatabase udb = new UserDatabase(trans);
    @Spy protected CertificateDatabase certdb = new CertificateDatabase(trans);
    @Spy protected EmailSubscriptionDatabase esdb = new EmailSubscriptionDatabase(trans);
    @Spy protected SharedFolderDatabase sfdb = new SharedFolderDatabase(trans);
    @Spy protected SharedFolderInvitationDatabase sfidb =
            new SharedFolderInvitationDatabase(trans);
    @Spy protected OrganizationInvitationDatabase oidb = new OrganizationInvitationDatabase(trans);

    // Can't use @Spy as Device.Factory's constructor needs a non-null certgen object.
    protected OrganizationDatabase odb = spy(new OrganizationDatabase(trans));

    // Can't use @Mock as Device.Factory's constructor needs a non-null certgen object.
    protected CertificateGenerator certgen = mock(CertificateGenerator.class);

    @Spy protected Organization.Factory factOrg = new Organization.Factory();
    @Spy protected SharedFolder.Factory factSharedFolder = new SharedFolder.Factory();
    @Spy protected Certificate.Factory factCert = new Certificate.Factory(certdb);
    @Spy protected Device.Factory factDevice = new Device.Factory();
    @Spy protected OrganizationInvitation.Factory factOrgInvite =
            new OrganizationInvitation.Factory();

    @Spy protected User.Factory factUser = new User.Factory(udb, oidb, factDevice, factOrg,
            factOrgInvite, factSharedFolder);
    {
        factDevice.inject(ddb, certdb, certgen, factUser, factCert);
        factSharedFolder.inject(sfdb, factUser);
        factOrg.inject(odb, factUser, factSharedFolder);
        factOrgInvite.inject(oidb, factUser, factOrg);
    }

    @Spy protected SharedFolderInvitation.Factory factSFI =
            new SharedFolderInvitation.Factory(sfidb, factUser, factSharedFolder);

    @Spy protected CertificateAuthenticator certificateAuthenticator =
            mock(CertificateAuthenticator.class);

    // Mock invitation emailer for use with sp.shareFolder calls and organization movement tests.
    @Spy protected MockInvitationEmailerFactory factEmailer;

    // To simulate service.signIn(USER, PASSWORD), subclasses can call setSessionUser(UserID)
    @Spy protected MockSessionUser sessionUser;

    @Spy PasswordManagement _passwordManagement = new PasswordManagement(db, factUser,
            mock(PasswordResetEmailer.class));

    // Subclasses can declare a @Mock'd or @Spy'd object for
    // - PasswordManagement,
    // - InvitationEmailer, or
    // - LocalTestSPDatabase
    // N.B. the @Mock is only necessary if the subclass will mock the object in some special way
    @InjectMocks protected SPService service;

    protected static final UserID USER_1 = UserID.fromInternal("user_1");
    protected static final byte[] USER_1_CRED = "CREDENTIALS".getBytes();

    protected static final UserID USER_2 = UserID.fromInternal("user_2");
    protected static final byte[] USER_2_CRED = "CREDENTIALS".getBytes();

    protected static final UserID USER_3 = UserID.fromInternal("user_3");
    protected static final byte[] USER_3_CRED = "CREDENTIALS".getBytes();

    // Use a method name that is unlikely to conflict with setup methods in subclasses
    @Before
    public void setupAbstractSPServiceTest()
            throws Exception
    {
        // Verkehr setup.
        service.setVerkehrClients_(verkehrPublisher, verkehrAdmin);
        service.setSessionInvalidator(sessionInvalidator);
        service.setUserTracker(userSessionTracker);

        OrganizationID orgId = OrganizationID.DEFAULT;
        AuthorizationLevel level = AuthorizationLevel.USER;

        // Add all the users to the db.
        trans.begin();
        udb.insertUser(USER_1, new FullName(USER_1.toString(), USER_1.toString()), USER_1_CRED,
                orgId, level);
        udb.setVerified(USER_1);
        udb.insertUser(USER_2, new FullName(USER_2.toString(), USER_2.toString()), USER_2_CRED,
                orgId, level);
        udb.setVerified(USER_2);
        udb.insertUser(USER_3, new FullName(USER_3.toString(), USER_3.toString()), USER_3_CRED,
                orgId, level);
        udb.setVerified(USER_3);
        trans.commit();
    }

    public static void addTestUser(UserDatabase udb, UserID userId)
            throws ExAlreadyExist, SQLException
    {
        udb.insertUser(userId, new FullName("first", "last"), SecUtil.newRandomBytes(10),
                OrganizationID.DEFAULT, AuthorizationLevel.USER);
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

    protected void setSessionUser(UserID userId)
    {
        sessionUser.set(factUser.create(userId));
    }

    protected void mockCertificateGeneratorAndIncrementSerialNumber() throws Exception
    {
        CertificateGenerationResult cert = mock(CertificateGenerationResult.class);

        // Just stub out the certificate generator. Make sure it doesn't try to contact the CA.
        when(certgen.generateCertificate(any(UserID.class), any(DID.class),
                any(PKCS10.class))).thenReturn(cert);

        when(cert.toString()).thenReturn(AbstractSPCertificateBasedTest.RETURNED_CERT);
        when(cert.getSerial()).thenReturn(++AbstractSPCertificateBasedTest._lastSerialNumber);

        // Just need some time in the future - say, one year.
        when(cert.getExpiry()).thenReturn(new Timestamp(System.currentTimeMillis() +
                1000L*60L*60L*24L*365L));
    }

    protected void mockCertificateAuthenticatorSetAuthenticatedState()
            throws ExBadCredential
    {
        when(certificateAuthenticator.isAuthenticated()).thenReturn(true);
        when(certificateAuthenticator.getSerial())
                .thenReturn(AbstractSPCertificateBasedTest._lastSerialNumber);
    }

    protected void mockCertificateAuthenticatorSetUnauthorizedState()
            throws ExBadCredential
    {
        when(certificateAuthenticator.isAuthenticated()).thenReturn(false);
        when(certificateAuthenticator.getSerial()).thenThrow(new ExBadCredential());
    }

    protected static ByteString newCSR(UserID userID, DID did)
            throws IOException, GeneralSecurityException
    {
        KeyPair kp = SecUtil.newRSAKeyPair();
        return ByteString.copyFrom(SecUtil.newCSR(kp.getPublic(), kp.getPrivate(), userID, did)
                .getEncoded());
    }
}
