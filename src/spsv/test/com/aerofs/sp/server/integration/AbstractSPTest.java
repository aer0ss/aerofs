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
import com.aerofs.servlets.SecUtilHelper;
import com.aerofs.sp.server.AbstractTestWithDatabase;
import com.aerofs.servlets.lib.db.jedis.JedisEpochCommandQueue;
import com.aerofs.sp.server.PasswordManagement;
import com.aerofs.sp.server.SPService;
import com.aerofs.sp.server.email.DeviceCertifiedEmailer;
import com.aerofs.sp.server.email.PasswordResetEmailer;
import com.aerofs.sp.server.email.RequestToSignUpEmailer;
import com.aerofs.sp.server.lib.EmailSubscriptionDatabase;
import com.aerofs.sp.server.lib.OrganizationInvitationDatabase;
import com.aerofs.sp.server.lib.SPDatabase;
import com.aerofs.sp.server.lib.SharedFolder;
import com.aerofs.sp.server.lib.SharedFolderDatabase;
import com.aerofs.sp.server.lib.cert.Certificate;
import com.aerofs.sp.server.lib.cert.CertificateDatabase;
import com.aerofs.sp.server.lib.cert.CertificateGenerator;
import com.aerofs.sp.server.lib.cert.CertificateGenerator.CertificationResult;
import com.aerofs.sp.server.lib.device.Device;
import com.aerofs.sp.server.lib.device.DeviceDatabase;
import com.aerofs.sp.server.lib.OrganizationDatabase;
import com.aerofs.sp.server.lib.UserDatabase;
import com.aerofs.sp.server.lib.id.OrganizationID;
import com.aerofs.sp.server.lib.organization.Organization;
import com.aerofs.sp.server.lib.organization.OrganizationInvitation;
import com.aerofs.sp.server.lib.session.CertificateAuthenticator;
import com.aerofs.sp.server.lib.user.AuthorizationLevel;
import com.aerofs.sp.server.lib.user.User;
import com.aerofs.sp.server.session.SPActiveTomcatSessionTracker;
import com.aerofs.sp.server.session.SPActiveUserSessionTracker;
import com.aerofs.sp.server.session.SPSessionInvalidator;
import com.aerofs.verkehr.client.lib.admin.VerkehrAdmin;
import com.aerofs.verkehr.client.lib.publisher.VerkehrPublisher;
import com.aerofs.proto.Cmd.Command;
import com.beust.jcommander.internal.Sets;
import com.google.common.collect.Lists;
import com.google.protobuf.ByteString;
import org.junit.Before;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import sun.security.pkcs.PKCS10;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Set;
import java.util.List;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * A base class for all tests using the SPService as the "seam"
 */
public class AbstractSPTest extends AbstractTestWithDatabase
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

    @Spy protected SPDatabase db = new SPDatabase(sqlTrans);
    @Spy protected DeviceDatabase ddb = new DeviceDatabase(sqlTrans);
    @Spy protected UserDatabase udb = new UserDatabase(sqlTrans);
    @Spy protected CertificateDatabase certdb = new CertificateDatabase(sqlTrans);
    @Spy protected EmailSubscriptionDatabase esdb = new EmailSubscriptionDatabase(sqlTrans);
    @Spy protected SharedFolderDatabase sfdb = new SharedFolderDatabase(sqlTrans);
    @Spy protected OrganizationInvitationDatabase oidb = new OrganizationInvitationDatabase(sqlTrans);

    // Can't use @Spy as Device.Factory's constructor needs a non-null certgen object.
    protected OrganizationDatabase odb = spy(new OrganizationDatabase(sqlTrans));

    // Can't use @Mock as Device.Factory's constructor needs a non-null certgen object.
    protected CertificateGenerator certgen = mock(CertificateGenerator.class);

    @Spy protected Organization.Factory factOrg = new Organization.Factory();
    @Spy protected SharedFolder.Factory factSharedFolder = new SharedFolder.Factory();
    @Spy protected Certificate.Factory factCert = new Certificate.Factory(certdb);
    @Spy protected Device.Factory factDevice = new Device.Factory();
    @Spy protected OrganizationInvitation.Factory factOrgInvite =
            new OrganizationInvitation.Factory();

    @Spy protected JedisEpochCommandQueue commandQueue = new JedisEpochCommandQueue(jedisTrans);

    @Spy protected User.Factory factUser = new User.Factory(udb, oidb, factDevice, factOrg,
            factOrgInvite, factSharedFolder);
    {
        factDevice.inject(ddb, certdb, certgen, factUser, factCert);
        factSharedFolder.inject(sfdb, factUser);
        factOrg.inject(odb, oidb, factUser, factSharedFolder, factOrgInvite);
        factOrgInvite.inject(oidb, factUser, factOrg);
    }

    @Spy protected CertificateAuthenticator certificateAuthenticator =
            mock(CertificateAuthenticator.class);

    // Mock invitation emailer for use with sp.shareFolder calls and organization movement tests.
    @Spy protected MockInvitationEmailerFactory factEmailer;

    // To simulate service.signIn(USER, PASSWORD), subclasses can call setSessionUser(UserID)
    @Spy protected MockSessionUser sessionUser;

    @Spy PasswordManagement passwordManagement = new PasswordManagement(db, factUser,
            mock(PasswordResetEmailer.class));
    @Spy DeviceCertifiedEmailer deviceCertifiedEmailer = mock(DeviceCertifiedEmailer.class);
    @Spy RequestToSignUpEmailer _requestToSignUpEmailer = mock(RequestToSignUpEmailer.class);

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

        // Add all the users to the db.
        sqlTrans.begin();
        factUser.create(USER_1).save(USER_1_CRED,
                new FullName(USER_1.toString(), USER_1.toString()), factOrg.getDefault());
        factUser.create(USER_2).save(USER_2_CRED,
                new FullName(USER_2.toString(), USER_2.toString()), factOrg.getDefault());
        factUser.create(USER_3).save(USER_3_CRED,
                new FullName(USER_3.toString(), USER_3.toString()), factOrg.getDefault());
        sqlTrans.commit();
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

    protected void setSessionUser(UserID userId)
    {
        sessionUser.set(factUser.create(userId));
    }

    protected void mockCertificateGeneratorAndIncrementSerialNumber() throws Exception
    {
        CertificationResult cert = mock(CertificationResult.class);

        // Just stub out the certificate generator. Make sure it doesn't try to contact the CA.
        when(certgen.generateCertificate(any(UserID.class), any(DID.class),
                any(PKCS10.class))).thenReturn(cert);

        when(cert.toString()).thenReturn(AbstractSPCertificateBasedTest.RETURNED_CERT);
        when(cert.getSerial()).thenReturn(++AbstractSPCertificateBasedTest._lastSerialNumber);

        // Just need some time in the future - say, one year.
        when(cert.getExpiry()).thenReturn(
                new Timestamp(System.currentTimeMillis() + 1000L * 60L * 60L * 24L * 365L));
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
        return ByteString.copyFrom( SecUtilHelper.serverOnlyNewCSR(kp.getPublic(), kp.getPrivate(),
                userID, did).getEncoded() );
    }

    protected Set<String> mockAndCaptureVerkehrPublish()
    {
        final Set<String> published = Sets.newHashSet();

        when(verkehrPublisher.publish_(any(String.class), any(byte[].class)))
                .then(new Answer<Object>() {
                    @Override
                    public Object answer(InvocationOnMock invocation)
                            throws Throwable
                    {
                        published.add((String)invocation.getArguments()[0]);
                        return UncancellableFuture.createSucceeded(null);
                    }
                });

        return published;
    }

    protected List<Command> mockAndCaptureVerkehrDeliverPayload()
    {
        final List<Command> payloads = Lists.newLinkedList();

        when(verkehrAdmin.deliverPayload(any(String.class), any(byte[].class)))
                .then(new Answer<Object>()
                {
                    @Override
                    public Object answer(InvocationOnMock invocation)
                            throws Throwable
                    {
                        byte[] bytes = (byte[]) invocation.getArguments()[1];
                        payloads.add(Command.parseFrom(bytes));
                        return UncancellableFuture.createSucceeded(null);
                    }
                });

        return payloads;
    }
}
