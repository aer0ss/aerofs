/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.integration;

import com.aerofs.audit.client.MockAuditClient;
import com.aerofs.base.BaseSecUtil;
import com.aerofs.base.acl.Permissions;
import com.aerofs.servlets.lib.ThreadLocalSFNotifications;
import com.aerofs.servlets.lib.analytics.AnalyticsClient;
import com.aerofs.base.async.UncancellableFuture;
import com.aerofs.base.ex.ExBadCredential;
import com.aerofs.ids.DID;
import com.aerofs.ids.SID;
import com.aerofs.ids.UserID;
import com.aerofs.lib.FullName;
import com.aerofs.servlets.MockSession;
import com.aerofs.servlets.SecUtilHelper;
import com.aerofs.servlets.lib.AsyncEmailSender;
import com.aerofs.servlets.lib.db.jedis.JedisEpochCommandQueue;
import com.aerofs.servlets.lib.ssl.CertificateAuthenticator;
import com.aerofs.sp.authentication.Authenticator;
import com.aerofs.sp.authentication.AuthenticatorFactory;
import com.aerofs.sp.authentication.LocalCredential;
import com.aerofs.sp.server.*;
import com.aerofs.sp.server.lib.user.AuthorizationLevel;
import com.aerofs.sp.server.url_sharing.UrlShare;
import com.aerofs.sp.server.url_sharing.UrlSharingDatabase;
import com.aerofs.sp.server.email.DeviceRegistrationEmailer;
import com.aerofs.sp.server.email.InvitationEmailer;
import com.aerofs.sp.server.email.PasswordResetEmailer;
import com.aerofs.sp.server.email.RequestToSignUpEmailer;
import com.aerofs.sp.server.email.SharedFolderNotificationEmailer;
import com.aerofs.sp.server.email.TwoFactorEmailer;
import com.aerofs.sp.server.lib.EmailSubscriptionDatabase;
import com.aerofs.sp.server.lib.License;
import com.aerofs.sp.server.lib.group.Group;
import com.aerofs.sp.server.lib.group.GroupDatabase;
import com.aerofs.sp.server.lib.group.GroupMembersDatabase;
import com.aerofs.sp.server.lib.group.GroupSharesDatabase;
import com.aerofs.sp.server.lib.organization.OrganizationDatabase;
import com.aerofs.sp.server.lib.organization.OrganizationInvitationDatabase;
import com.aerofs.sp.server.lib.SPDatabase;
import com.aerofs.sp.server.lib.SPParam;
import com.aerofs.sp.server.lib.sf.SharedFolder;
import com.aerofs.sp.server.lib.sf.SharedFolderDatabase;
import com.aerofs.sp.server.lib.user.UserDatabase;
import com.aerofs.sp.server.lib.cert.Certificate;
import com.aerofs.sp.server.lib.cert.CertificateDatabase;
import com.aerofs.sp.server.lib.cert.CertificateGenerator;
import com.aerofs.sp.server.lib.cert.CertificateGenerator.CertificationResult;
import com.aerofs.sp.server.lib.device.Device;
import com.aerofs.sp.server.lib.device.DeviceDatabase;
import com.aerofs.sp.server.lib.organization.Organization;
import com.aerofs.sp.server.lib.organization.OrganizationInvitation;
import com.aerofs.sp.server.lib.session.RequestRemoteAddress;
import com.aerofs.sp.server.lib.twofactor.TwoFactorAuthDatabase;
import com.aerofs.sp.server.lib.user.User;
import com.aerofs.sp.server.session.SPActiveTomcatSessionTracker;
import com.aerofs.sp.server.session.SPActiveUserSessionTracker;
import com.aerofs.sp.server.session.SPSessionInvalidator;
import com.aerofs.sp.server.settings.token.UserSettingsToken;
import com.aerofs.sp.server.settings.token.UserSettingsTokenDatabase;
import com.aerofs.sp.server.sharing_rules.SharingRulesFactory;
import com.aerofs.ssmp.*;
import com.aerofs.ssmp.SSMPRequest.Type;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.protobuf.ByteString;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.junit.Before;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyVararg;
import static org.mockito.Mockito.*;

/**
 * A base class for all tests using the SPService as the "seam"
 *
 * TODO (AT):
 *  - get rid of USER_1, USER_2, and USER_3.
 *  - saveUser should require explicit org and auth level.
 */
public class AbstractSPTest extends AbstractTestWithDatabase
{
    public final class Published
    {
        public Type type;
        public SSMPIdentifier topic;
        public byte[] bytes;

        public Published(Type type, SSMPIdentifier topic, byte[] bytes)
        {
            this.type = type;
            this.topic = topic;
            this.bytes = bytes;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Published published = (Published)o;

            return Arrays.equals(bytes, published.bytes) && topic.equals(published.topic);
        }

        @Override
        public int hashCode()
        {
            return Objects.hashCode(topic, bytes);
        }
    }

    protected SSMPConnection ssmp = mock(SSMPConnection.class);
    // this is only spied because of @InjectMocks.
    @Spy protected MockAuditClient auditClient = new MockAuditClient(System.out::println);

    @Spy protected AnalyticsClient analyticsClient = mock(AnalyticsClient.class);

    protected SPActiveUserSessionTracker userSessionTracker =
            new SPActiveUserSessionTracker();
    protected SPActiveTomcatSessionTracker tomcatSessionTracker =
            new SPActiveTomcatSessionTracker();
    @Spy protected SPSessionInvalidator sessionInvalidator = new SPSessionInvalidator(
            userSessionTracker, tomcatSessionTracker);

    @Spy protected SPDatabase db = new SPDatabase(sqlTrans);
    @Spy protected DeviceDatabase ddb = new DeviceDatabase(sqlTrans);
    @Spy protected UserDatabase udb = new UserDatabase(sqlTrans);
    @Spy protected TwoFactorAuthDatabase tfdb = new TwoFactorAuthDatabase(sqlTrans);
    @Spy protected CertificateDatabase certdb = new CertificateDatabase(sqlTrans);
    @Spy protected EmailSubscriptionDatabase esdb = new EmailSubscriptionDatabase(sqlTrans);
    @Spy protected SharedFolderDatabase sfdb = new SharedFolderDatabase(sqlTrans);
    @Spy protected OrganizationInvitationDatabase oidb = new OrganizationInvitationDatabase(sqlTrans);
    @Spy protected UrlSharingDatabase usdb = new UrlSharingDatabase(sqlTrans);
    @Spy protected UserSettingsTokenDatabase ustdb = new UserSettingsTokenDatabase(sqlTrans);
    @Spy protected final GroupDatabase gdb = new GroupDatabase(sqlTrans);
    @Spy protected final GroupMembersDatabase gmdb = new GroupMembersDatabase(sqlTrans);
    @Spy protected final GroupSharesDatabase gsdb = new GroupSharesDatabase(sqlTrans);

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
    @Spy protected UrlShare.Factory factUrlShare = new UrlShare.Factory(usdb);
    @Spy protected UserSettingsToken.Factory factUserSettingsToken = new UserSettingsToken.Factory();
    @Spy protected final Group.Factory factGroup = new Group.Factory();

    @Spy protected JedisEpochCommandQueue commandQueue = new JedisEpochCommandQueue(jedisTrans);
    @Spy protected ThreadLocalSFNotifications sfNotifications = new ThreadLocalSFNotifications();
    // Can't use @Spy as User.Factory's constructor needs a spied instance;
    protected License license = spy(new License());

    @Spy protected User.Factory factUser = new User.Factory();
    {
        factUser.inject(udb, oidb, tfdb, gmdb, factDevice, factOrg,
                factOrgInvite, factSharedFolder, factGroup, license);
        factDevice.inject(ddb, certdb, certgen, factUser, factCert);
        factSharedFolder.inject(sfdb, gsdb, factGroup, factUser, sfNotifications);
        factOrg.inject(odb, oidb, factUser, factSharedFolder, factOrgInvite, factGroup, gdb);
        factOrgInvite.inject(oidb, factUser, factOrg);
        factGroup.inject(gdb, gmdb, gsdb, factOrg, factSharedFolder, factUser);
        factUserSettingsToken.inject(ustdb);
    }

    @Spy protected CertificateAuthenticator certificateAuthenticator =
            mock(CertificateAuthenticator.class);
    @Spy protected RequestRemoteAddress remoteAddress = mock(RequestRemoteAddress.class);

    // use a mock instead of a spy lest the box happen to have a local mail relay running.
    @Mock AsyncEmailSender asyncEmailSender;

    @Mock protected InvitationEmailer.Factory factEmailer;

    // To simulate service.signIn(USER, PASSWORD), subclasses can call setSession(UserID)
    @Spy protected MockSession session;

    @Spy protected ACLNotificationPublisher aclNotificationPublisher = new ACLNotificationPublisher(factUser, ssmp);
    protected SFNotificationPublisher sfNotificationPublisher = mock(SFNotificationPublisher.class);

    @Spy protected Authenticator authenticator = spy(new AuthenticatorFactory(aclNotificationPublisher, auditClient, analyticsClient).create());
    @Spy PasswordManagement passwordManagement = new PasswordManagement(db, factUser,
            mock(PasswordResetEmailer.class), authenticator);
    @Spy DeviceRegistrationEmailer _deviceRegistrationEmailer = mock(DeviceRegistrationEmailer.class);
    @Spy RequestToSignUpEmailer requestToSignUpEmailer = mock(RequestToSignUpEmailer.class);
    @Spy TwoFactorEmailer twoFactorEmailer = mock(TwoFactorEmailer.class);

    @Mock SharedFolderNotificationEmailer sharedFolderNotificationEmailer;

    @Spy protected IdentitySessionManager identitySessionManager = new IdentitySessionManager(jedisProvider);
    @Spy protected SharingRulesFactory sharingRules = new SharingRulesFactory(authenticator,
            factUser, sharedFolderNotificationEmailer);

    @Mock protected JedisRateLimiter rateLimiter;

    @Mock ScheduledExecutorService scheduledExecutorService;

    @Mock protected Zelda zelda;
    @Spy protected AccessCodeProvider accessCodeProvider
            = new AccessCodeProvider(auditClient, identitySessionManager);

    // Subclasses can declare a @Mock'd or @Spy'd object for
    // - PasswordManagement,
    // - InvitationEmailer, or
    // - LocalTestSPDatabase
    // N.B. the @Mock is only necessary if the subclass will mock the object in some special way
    @InjectMocks protected SPService service;

    private final List<Published> allPublished = Lists.newArrayList();

    // N.B. These users are DEPRECATED. Do not use them in the new code. Use newUser(), saveUser(), etc.
    protected final User USER_1 /* DEPRECATED */ = factUser.create(UserID.fromInternal("user_1"));
    protected final User USER_2 /* DEPRECATED */ = factUser.create(UserID.fromInternal("user_2"));
    protected final User USER_3 /* DEPRECATED */ = factUser.create(UserID.fromInternal("user_3"));
    protected static final byte[] CRED = "CREDENTIALS".getBytes();

    private int nextUserID;

    // Use a method name that is unlikely to conflict with setup methods in subclasses
    @Before
    public void setupAbstractSPServiceTest()
            throws Exception
    {
        nextUserID = 1;

        mockInvitationEmailerFactory();
        wireSPService();
        mockLicense();
        mockRateLimiter();

        // mock out ssmp
        when(ssmp.request(any(SSMPRequest.class))).then(invocation -> {
            SSMPRequest r = (SSMPRequest) invocation.getArguments()[0];
            allPublished.add(new Published(r.type, r.to, r.payload));
            return UncancellableFuture.createSucceeded(new SSMPResponse(SSMPResponse.OK, ""));
        });

        ///////////////////////////////////////////////////////////////////////////
        // The method to populate the database below is outdated. See methods in
        // AbstractBusinessObjectTest for a better approach
        ///////////////////////////////////////////////////////////////////////////

        sqlTrans.begin();

        saveUser(USER_1);
        saveUser(USER_2);
        saveUser(USER_3);

        sqlTrans.commit();
    }

    protected void mockLicense()
    {
        when(license.isValid()).thenReturn(true);
        when(license.seats()).thenReturn(Integer.MAX_VALUE);
    }

    private void mockInvitationEmailerFactory()
            throws Exception
    {
        when(factEmailer.createFolderInvitationEmailer(any(User.class), any(User.class), any(String.class),
                any(String.class), any(SID.class), any(Permissions.class))).then(RETURNS_MOCKS);
        when(factEmailer.createOrganizationInvitationEmailer(any(User.class), any(User.class)))
                .then(RETURNS_MOCKS);
        when(factEmailer.createSharedFolderSignUpInvitationEmailer(any(User.class), any(User.class),
                any(String.class), any(Permissions.class), any(String.class), any(String.class)))
                .then(RETURNS_MOCKS);
        when(factEmailer.createSignUpInvitationEmailer(any(User.class), any(User.class),
                any(String.class))).then(RETURNS_MOCKS);
        when(factEmailer.createGroupSignUpInvitationEmailer(any(User.class), any(User.class),
                any(Group.class), any(String.class))).then(RETURNS_MOCKS);
        when(factEmailer.createAddedToGroupEmailer(any(User.class), any(User.class),
                any(Group.class))).then(RETURNS_MOCKS);
        when(factEmailer.createBatchInvitationEmailer(any(User.class), any(User.class),
                any(Group.class), any())).then(RETURNS_MOCKS);
    }

    private void mockRateLimiter()
    {
        // false = does not exceed rate limit
        when(rateLimiter.update((String)anyVararg())).thenReturn(false);
    }

    // we occasionally need this, #sigh
    protected void rebuildSPService()
    {
        service = new SPService(db,
                sqlTrans,
                jedisTrans,
                session,
                passwordManagement,
                certificateAuthenticator,
                remoteAddress,
                factUser,
                factOrg,
                factOrgInvite,
                factDevice,
                esdb,
                factSharedFolder,
                factEmailer,
                _deviceRegistrationEmailer,
                requestToSignUpEmailer,
                twoFactorEmailer,
                commandQueue,
                identitySessionManager,
                authenticator,
                sharingRules,
                sharedFolderNotificationEmailer,
                asyncEmailSender,
                factUrlShare,
                factUserSettingsToken,
                factGroup,
                rateLimiter,
                scheduledExecutorService,
                ssmp,
                auditClient,
                aclNotificationPublisher,
                zelda,
                accessCodeProvider,
                analyticsClient,
                sfNotificationPublisher,
                sfNotifications);
        wireSPService();
    }

    // Do wiring for SP after its construction
    protected void wireSPService()
    {
        service.setSessionInvalidator(sessionInvalidator);
        service.setUserTracker(userSessionTracker);
    }

    /**
     * Create a new User object without saving the user to the db
     */
    protected User newUser()
    {
        return factUser.create(UserID.fromInternal("u" + Integer.toString(++nextUserID) + "@email"));
    }

    /**
     * Create a new User object and save it to the db
     *
     * N.B. SQL transaction is required for this method:
     *
     *      sqlTrans.begin();
     *      ...
     *      User u = saveUser();
     *      ...
     *      sqlTrans.commit();
     */
    protected User saveUser()
            throws Exception
    {
        User user = newUser();
        saveUser(user);
        return user;
    }

    protected User saveUserWithNewOrganization()
            throws Exception
    {
        User user = saveUser();
        user.setOrganization(saveOrganization(), AuthorizationLevel.ADMIN);
        return user;
    }

    /**
     * Create a user with first name last names identical to the user id.
     * Must be called within SQL transaction
     */
    public static void saveUser(User user)
            throws Exception
    {
        String idString = user.id().getString();
        user.save(SPParam.getShaedSP(LocalCredential.deriveKeyForUser(user.id(), CRED)),
                new FullName(idString, idString));
    }

    protected Device saveDevice(User owner)
            throws Exception
    {
        DID did = DID.generate();
        return factDevice.create(did).save(owner, "", "", owner.toString() + "'s device " + did);
    }

    protected Organization saveOrganization() throws SQLException
    {
        return factOrg.save();
    }

    // TODO (WW) remove this method as it doesn't do much
    protected void setSession(User user)
    {
        session.setUser(user);
        session.setBasicAuthDate(System.currentTimeMillis());
        session.setCertificateAuthDate(System.currentTimeMillis());
    }

    protected void mockCertificateGeneratorAndIncrementSerialNumber() throws Exception
    {
        CertificationResult cert = mock(CertificationResult.class);

        // Just stub out the certificate generator. Make sure it doesn't try to contact the CA.
        when(certgen.generateCertificate(any(UserID.class), any(DID.class),
                any(PKCS10CertificationRequest.class))).thenReturn(cert);

        when(cert.toString()).thenReturn(AbstractSPCertificateBasedTest.RETURNED_CERT);
        when(cert.getSerial()).thenReturn(++AbstractSPCertificateBasedTest._lastSerialNumber);

        // Just need some time in the future - say, one year.
        when(cert.getExpiry()).thenReturn(
                new Timestamp(System.currentTimeMillis() + 1000L * 60L * 60L * 24L * 365L));
    }

    protected void mockCertificateAuthenticatorSetAuthenticatedState(User user, Device device)
            throws ExBadCredential
    {
        when(certificateAuthenticator.isAuthenticated()).thenReturn(true);
        when(certificateAuthenticator.getSerial())
                .thenReturn(AbstractSPCertificateBasedTest._lastSerialNumber);
        when(certificateAuthenticator.getCName())
                .thenReturn(BaseSecUtil.getCertificateCName(user.id(), device.id()));
    }

    protected static ByteString newCSR(User user, Device device)
            throws IOException, GeneralSecurityException
    {
        KeyPair kp = BaseSecUtil.newRSAKeyPair();
        return ByteString.copyFrom(SecUtilHelper.serverOnlyNewCSR(kp.getPublic(), kp.getPrivate(),
                user.id(), device.id()).getEncoded());
    }

    protected List<Published> getPublishedMessages()
    {
        return ImmutableList.copyOf(allPublished);
    }

    protected Set<SSMPIdentifier> getTopicsPublishedTo()
    {
        Set<SSMPIdentifier> topicNames = Sets.newHashSet();

        for (Published published : allPublished) {
            topicNames.add(published.topic);
        }

        return topicNames;
    }

    protected void assertPublishedTo(User... users)
    {
        Set<SSMPIdentifier> topicsPublishedTo = getTopicsPublishedTo();

        for (User user : users) {
            if (!topicsPublishedTo.contains(SSMPIdentifiers.getACLTopic(user.id().getString()))) {
                fail("ssmp publish doesn't contain " + user + ". actual: " + allPublished);
            }
        }
    }

    protected void assertPublishedOnlyTo(User... users)
            throws Exception
    {
        assertPublishedTo(users);

        // get team server users
        Set<User> tsUsers = Sets.newHashSet();
        sqlTrans.begin();
        for (User user : users) tsUsers.add(user.getOrganization().getTeamServerUser());
        sqlTrans.commit();

        // all the team servers must be included
        assertPublishedTo(tsUsers.toArray(new User[tsUsers.size()]));

        if (users.length + tsUsers.size() != allPublished.size()) {
            fail("ssmp publish has more than expected: " + Arrays.toString(users) + " actual: " + allPublished);
        }
    }

    protected void assertNothingPublished()
            throws Exception
    {
        assertThat(allPublished.isEmpty(), equalTo(true));
    }

    // TODO (WW) is this method useful?
    protected void clearPublishedMessages()
    {
        allPublished.clear();
    }
}
