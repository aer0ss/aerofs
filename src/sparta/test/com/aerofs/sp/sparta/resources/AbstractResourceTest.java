/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.sparta.resources;

import com.aerofs.audit.client.AuditClient;
import com.aerofs.audit.client.MockAuditClient;
import com.aerofs.base.BaseSecUtil;
import com.aerofs.base.BaseUtil;
import com.aerofs.base.ElapsedTimer;
import com.aerofs.base.Loggers;
import com.aerofs.base.acl.Permissions;
import com.aerofs.base.config.ConfigurationProperties;
import com.aerofs.base.id.GroupID;
import com.aerofs.base.id.OrganizationID;
import com.aerofs.base.id.RestObject;
import com.aerofs.bifrost.oaaas.auth.NonceChecker;
import com.aerofs.ids.DID;
import com.aerofs.ids.SID;
import com.aerofs.ids.UserID;
import com.aerofs.bifrost.oaaas.model.Client;
import com.aerofs.bifrost.oaaas.model.ResourceServer;
import com.aerofs.bifrost.server.Bifrost;
import com.aerofs.bifrost.server.BifrostTest;
import com.aerofs.lib.FullName;
import com.aerofs.lib.injectable.TimeSource;
import com.aerofs.servlets.lib.db.BifrostDatabaseParams;
import com.aerofs.servlets.lib.db.LocalTestDatabaseConfigurator;
import com.aerofs.servlets.lib.db.SPDatabaseParams;
import com.aerofs.servlets.lib.db.sql.SQLThreadLocalTransaction;
import com.aerofs.sp.client.SPBlockingClient;
import com.aerofs.sp.server.*;
import com.aerofs.sp.server.lib.License;
import com.aerofs.sp.server.lib.device.Device;
import com.aerofs.sp.server.lib.group.Group;
import com.aerofs.sp.server.lib.organization.Organization;
import com.aerofs.sp.server.lib.organization.OrganizationInvitation;
import com.aerofs.sp.server.lib.sf.SharedFolder;
import com.aerofs.sp.server.lib.user.AuthorizationLevel;
import com.aerofs.sp.server.lib.user.User;
import com.aerofs.sp.server.lib.user.UserDatabase;
import com.aerofs.sp.server.url_sharing.UrlShare;
import com.aerofs.sp.sparta.Sparta;
import com.aerofs.ssmp.SSMPConnection;
import com.aerofs.ssmp.SSMPRequest;
import com.aerofs.ssmp.SSMPResponse;
import com.aerofs.testlib.AbstractBaseTest;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.SettableFuture;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.inject.*;
import com.jayway.restassured.RestAssured;
import com.jayway.restassured.config.ObjectMapperConfig;
import com.jayway.restassured.config.RestAssuredConfig;
import com.jayway.restassured.mapper.factory.GsonObjectMapperFactory;
import com.jayway.restassured.specification.RequestSpecification;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.jboss.netty.handler.codec.http.HttpHeaders.Names;
import org.jboss.netty.util.HashedWheelTimer;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.mockito.Mock;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;
import java.util.Properties;
import java.util.TimeZone;
import java.util.*;

import static com.aerofs.bifrost.server.BifrostTest.*;
import static com.aerofs.sp.sparta.resources.SharedFolderResource.aclEtag;
import static com.google.common.base.Strings.isNullOrEmpty;
import static com.jayway.restassured.RestAssured.given;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

public class AbstractResourceTest extends AbstractBaseTest
{
    protected static final Logger l = Loggers.getLogger(AbstractResourceTest.class);

    private static final SessionFactory sessionFactory = mock(SessionFactory.class);
    private static final Session session = mock(Session.class);
    protected static Injector bifrostInj;
    private static Bifrost bifrost;
    private static final NonceChecker nonceChecker = mock(NonceChecker.class);
    protected static String deploymentSecret = "81706d9d9cbdbc4e6f14e08117cfcd73";

    protected Sparta sparta;
    protected Injector inj;

    protected static final UserID user = UserID.fromInternal("user@bar.baz");
    protected static final UserID admin = UserID.fromInternal("admin@bar.baz");
    protected static final UserID other = UserID.fromInternal("other@bar.baz");
    protected static final UserID otherOrg = UserID.fromInternal("outsideorg@bar.baz");
    protected static final OrganizationID otherOrgID = new OrganizationID(3);

    private static final String RO_SELF = "roself";
    private static final String RW_SELF = "rwself";
    private static final String ADMIN = "admin";
    private static final String OTHER = "other";
    private static final String NO_GROUPS = "nogroups";
    private static final String DIFFERENT_ORG = "differentorg";
    private static final String LINK_SHARING = "linksharing";

    private static final SSMPConnection ssmp = mock(SSMPConnection.class);
    private static final CommandDispatcher commandDispatcher = mock(CommandDispatcher.class);
    protected static final PasswordManagement passwordManagement = mock(PasswordManagement.class);
    protected static final License license = mock(License.class);

    @Mock ACLNotificationPublisher aclNotificationPublisher;
    protected static final MockAuditClient auditClient = new MockAuditClient(
            content -> l.info("audit: {}", content));

    private static final SPDatabaseParams dbParams = new SPDatabaseParams();

    protected SQLThreadLocalTransaction sqlTrans;
    protected User.Factory factUser;
    protected SharedFolder.Factory factSF;
    protected Group.Factory factGroup;
    protected Device.Factory factDevice;
    protected OrganizationInvitation.Factory factInvitation;
    protected UrlShare.Factory factUrlShare;
    private int nextUserID = 1;

    private static final ThreadLocal<DateFormat> _dateFormat = new ThreadLocal<DateFormat>() {
        @Override
        public DateFormat initialValue() {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
            f.setTimeZone(TimeZone.getTimeZone("UTC"));
            return f;
        }
    };

    static Matcher<String> isValidDate()
    {
        return new BaseMatcher<String>() {
            @Override
            public boolean matches(Object item) {
                try {
                    Date d = _dateFormat.get().parse((String)item);
                    return true;
                } catch (Exception e) {
                    return false;
                }
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("valid date");
            }
        };
    }

    @BeforeClass
    public static void commonSetup() throws Exception
    {
        Properties prop = new Properties();
        prop.setProperty("bifrost.port", "0");
        ConfigurationProperties.setProperties(prop);

        when(license.isValid()).thenReturn(true);
        when(license.seats()).thenReturn(Integer.MAX_VALUE);

        when(sessionFactory.openSession()).thenReturn(session);
        // FIXME(AT): this is technically incorrect if getCurrentSession() is called before openSession()
        when(sessionFactory.getCurrentSession()).thenReturn(session);

        when(ssmp.request(any(SSMPRequest.class)))
                .thenAnswer(invocation -> {
                    SettableFuture<SSMPResponse> f = SettableFuture.create();
                    f.set(new SSMPResponse(SSMPResponse.OK, ""));
                    return f;
                });

        ElapsedTimer t = new ElapsedTimer();

        // start OAuth service
        bifrostInj = bifrostInjector();
        bifrost = new Bifrost(bifrostInj, deploymentSecret);
        bifrost.start();
        l.info("OAuth service at {}", bifrost.getListeningPort());

        System.out.println("started bifrost in " + t.elapsed());

        String bifrostUrl =
                "http://localhost:" + bifrost.getListeningPort();

        prop.setProperty("sparta.port", "0");
        prop.setProperty("sparta.host", "localhost");
        prop.setProperty("sparta.oauth.id", BifrostTest.RESOURCEKEY);
        prop.setProperty("sparta.oauth.secret", BifrostTest.RESOURCESECRET);
        prop.setProperty("sparta.oauth.url", bifrostUrl);
        ConfigurationProperties.setProperties(prop);

        RestAssured.config = RestAssuredConfig.config()
                .objectMapperConfig(ObjectMapperConfig.objectMapperConfig()
                        .gsonObjectMapperFactory(new GOMF()));

        LocalTestDatabaseConfigurator.resetDB(new BifrostDatabaseParams());
        LocalTestDatabaseConfigurator.initializeLocalDatabase(dbParams);
    }

    private static class GOMF implements GsonObjectMapperFactory {
        @Override
        @SuppressWarnings("rawtypes")
        public Gson create(Class cls, String charset)
        {
            return new GsonBuilder()
                    .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();
        }
    }

    @AfterClass
    public static void commonCleanup()
    {
        bifrost.stop();
    }

    private static Injector bifrostInjector() throws Exception
    {
        final SPBlockingClient.Factory factSP = mock(SPBlockingClient.Factory.class);
        SPBlockingClient sp = mock(SPBlockingClient.class);
        when(factSP.create()).thenReturn(sp);
        when(sp.signInRemote()).thenReturn(sp);

        Injector inj = Guice.createInjector(Bifrost.bifrostModule(),
                BifrostTest.mockDatabaseModule(sessionFactory), new AbstractModule() {
            @Override
            protected void configure()
            {
                bind(SPBlockingClient.Factory.class).toInstance(factSP);
                bind(NonceChecker.class).toInstance(nonceChecker);
            }
        });

        ResourceServer rs = createResourceServer(inj, RESOURCEKEY, RESOURCESECRET,
                ImmutableSet.of("files.read", "files.write"));
        Client client = createClient(inj, rs, BifrostTest.CLIENTID, BifrostTest.CLIENTSECRET,
                BifrostTest.CLIENTNAME, ImmutableSet.of("files.read", "files.write"), 0L);

        createAccessToken(client, inj, RW_SELF, user, OrganizationID.PRIVATE_ORGANIZATION, 0,
                ImmutableSet.of("files.read", "user.read", "acl.read",
                        "files.write", "user.write", "acl.write",
                        "acl.invitations", "groups.read", "user.password"));
        createAccessToken(client, inj, RO_SELF, user, OrganizationID.PRIVATE_ORGANIZATION, 0,
                ImmutableSet.of("files.read", "user.read", "acl.read", "acl.invitations", "groups.read"));
        createAccessToken(client, inj, ADMIN,
                OrganizationID.PRIVATE_ORGANIZATION.toTeamServerUserID(),
                OrganizationID.PRIVATE_ORGANIZATION, 0,
                ImmutableSet.of("user.read", "acl.read", "organization.admin", "user.write",
                        "user.password", "acl.write", "acl.invitations", "groups.read"));
        createAccessToken(client, inj, LINK_SHARING, user, OrganizationID.PRIVATE_ORGANIZATION, 0,
                ImmutableSet.of("files.read", "linksharing"));
        createAccessToken(client, inj, OTHER, other, OrganizationID.PRIVATE_ORGANIZATION, 0,
                ImmutableSet.of("user.read", "acl.read", "user.write", "acl.write",
                        "acl.invitations", "groups.read"));
        createAccessToken(client, inj, NO_GROUPS, user, OrganizationID.PRIVATE_ORGANIZATION, 0,
                ImmutableSet.of("user.read", "acl.read", "acl.invitations"));
        createAccessToken(client, inj, DIFFERENT_ORG, otherOrg, otherOrgID, 0,
                ImmutableSet.of("user.read", "acl.read", "organization.admin", "user.write",
                        "user.password", "acl.write", "acl.invitations", "groups.read"));

        createResourceServer(inj, "oauth-havre", "fake-havre-secret",
                ImmutableSet.of("files.read", "linksharing"));

        return inj;
    }

    private static Injector spartaInjector() throws Exception
    {
        return Guice.createInjector(
                Sparta.spartaModule(new HashedWheelTimer(), deploymentSecret),
                Sparta.databaseModule(dbParams.getProvider()), new AbstractModule()
        {
            @Override
            protected void configure()
            {
                bind(SSMPConnection.class).toInstance(ssmp);
                bind(CommandDispatcher.class).toInstance(commandDispatcher);
                bind(PasswordManagement.class).toInstance(passwordManagement);
                bind(AuditClient.class).toInstance(auditClient);
                bind(License.class).toInstance(license);
                // use a spy in cases where we do not mock the time.
                bind(TimeSource.class).toInstance(spy(new TimeSource()));
                // mock access code provider to work with mocked nonce checker
                bind(AccessCodeProvider.class).toInstance(createMockAccessCodeProvider());
            }
        });
    }

    private static AccessCodeProvider createMockAccessCodeProvider()
    {
        AccessCodeProvider accessCodeProvider = mock(AccessCodeProvider.class);

        doAnswer(invocation -> {
            User user = invocation.getArgumentAt(0, User.class);
            String accessCode = UUID.randomUUID().toString();

            doReturn(new NonceChecker.AuthorizedClient(
                    user.id(),
                    user.getOrganization().id(),
                    user.isAdmin()))
            .when(nonceChecker)
                    .authorizeAPIClient(eq(accessCode), anyString());

            return accessCode;
        }).when(accessCodeProvider).createAccessCodeForUser(any(User.class));

        return accessCodeProvider;
    }

    @Before
    public void setUp() throws Exception
    {
        inj = spartaInjector();
        sqlTrans = inj.getInstance(SQLThreadLocalTransaction.class);
        factUser = inj.getInstance(User.Factory.class);
        factSF = inj.getInstance(SharedFolder.Factory.class);
        factGroup = inj.getInstance(Group.Factory.class);
        factDevice = inj.getInstance(Device.Factory.class);
        factGroup = inj.getInstance(Group.Factory.class);
        factInvitation = inj.getInstance(OrganizationInvitation.Factory.class);
        UserDatabase userDatabase = inj.getInstance(UserDatabase.class);
        factUrlShare = inj.getInstance(UrlShare.Factory.class);
        Organization.Factory factOrg = inj.getInstance(Organization.Factory.class);

        sqlTrans.begin();
        try (Statement s = sqlTrans.getConnection().createStatement()) {
            for (String table : SPDatabaseParams.TABLES) {
                s.execute("delete from sp_" + table);
            }
        }
        Organization org = factOrg.save(OrganizationID.PRIVATE_ORGANIZATION);

        User u = factUser.create(user);
        u.save(new byte[0], new FullName("User", "Foo"));
        User o = factUser.create(other);
        o.save(new byte[0], new FullName("Other", "Foo"));
        User a = factUser.create(admin);
        a.save(new byte[0], new FullName("Admin", "Foo"));
        User oo = factUser.create(otherOrg);
        oo.save(new byte[0], new FullName("Other", "Org"));
        oo.setOrganization(factOrg.save(otherOrgID), AuthorizationLevel.ADMIN);

        a.setOrganization(org, AuthorizationLevel.ADMIN);
        u.setOrganization(org, AuthorizationLevel.USER);
        o.setOrganization(org, AuthorizationLevel.USER);

        String signupCode = "CODE";
        userDatabase.insertSignupCode(signupCode, user);
        factInvitation.save(a, u, org, signupCode);

        sqlTrans.commit();

        sparta = new Sparta(inj, deploymentSecret);
        sparta.start();

        RestAssured.baseURI = "http://localhost";
        RestAssured.port = sparta.getListeningPort();
        l.info("Sparta service at {}", RestAssured.port);
    }

    @After
    public void tearDown() throws Exception
    {
        if (sqlTrans.isInTransaction()) sqlTrans.rollback();
        sqlTrans.cleanUp();

        if (sparta != null) {
            sparta.stop();
        }
    }

    protected RequestSpecification givenAccess(String token)
    {
        return given()
                .header(Names.AUTHORIZATION, "Bearer " + token);
    }

    protected RequestSpecification givenReadAccess() { return givenAccess(RO_SELF); }
    protected RequestSpecification givenWriteAccess() { return givenAccess(RW_SELF); }
    protected RequestSpecification givenLinkSharingAccess() { return givenAccess(LINK_SHARING); }
    protected RequestSpecification givenAdminAccess() { return givenAccess(ADMIN); }
    protected RequestSpecification givenOtherAccess() { return givenAccess(OTHER); }
    protected RequestSpecification givenNoGroupAccess() { return givenAccess(NO_GROUPS); }
    protected RequestSpecification givenOtherOrgAccess() { return givenAccess(DIFFERENT_ORG); }

    private static final Base64.Encoder base64 = Base64.getEncoder();

    protected static String encode(UserID user)
    {
        return BaseUtil.utf2string(base64.encode(BaseUtil.string2utf(user.getString())));
    }

    protected RequestSpecification givenCert(DID did, UserID user, long serial)
    {
        return given()
                .header(Names.AUTHORIZATION,
                        "Aero-Device-Cert " + encode(user) + " " + did.toStringFormal())
                .header("DName", "CN=" + BaseSecUtil.getCertificateCName(user, did))
                .header("Serial", Long.toString(serial, 16))
                .header("Verify", "SUCCESS");
    }

    protected RequestSpecification givenSecret(String service, String secret, UserID user, DID did)
    {
        return given()
                .header(Names.AUTHORIZATION, "Aero-Delegated-User-Device "
                        + service + " " + secret + " "
                        + encode(user) + " " + did.toStringFormal());
    }

    protected RequestSpecification givenSecret(String service, String secret)
    {
        return given()
                .header(Names.AUTHORIZATION, "Aero-Service-Shared-Secret " + service + " " + secret);
    }

    protected void mkUser(String userid, String first, String last) throws Exception
    {
        sqlTrans.begin();
        factUser.create(UserID.fromInternal(userid))
                .save(new byte[0], new FullName(first, last));
        sqlTrans.commit();
    }

    protected void mkGroup(GroupID gid, String name) throws Exception
    {
        sqlTrans.begin();
        factGroup.save(gid, name, OrganizationID.PRIVATE_ORGANIZATION, null);
        sqlTrans.commit();
    }

    protected void mkDevice(DID did, UserID owner, String name, String osFamily, String osName)
            throws Exception
    {
        sqlTrans.begin();
        factDevice.create(did).save(factUser.create(owner), osFamily, osName, name);
        sqlTrans.commit();
    }

    protected GroupID mkGroup(String name) throws Exception
    {
        sqlTrans.begin();
        GroupID ret = factGroup.save(name, OrganizationID.PRIVATE_ORGANIZATION, null).id();
        sqlTrans.commit();
        return ret;
    }
    protected SID mkShare(String name, String owner) throws Exception
    {
        return mkShare(name, UserID.fromExternal(owner));
    }

    protected SID mkShare(String name, UserID owner) throws Exception
    {
        SID sid = SID.generate();
        sqlTrans.begin();
        User user = factUser.create(owner);
        factSF.create(sid).save(name, user);
        sqlTrans.commit();
        return sid;
    }

    protected String mkUrl(RestObject soid, String accessToken, UserID createdBy,
            boolean requiresLogin, @Nullable String password, @Nullable Long expires)
            throws Exception
    {
        sqlTrans.begin();
        UrlShare urlShare = factUrlShare.save(soid, accessToken, createdBy);

        if (requiresLogin) {
            urlShare.setRequireLogin(true, accessToken);
        }

        if (!isNullOrEmpty(password)) {
            urlShare.setPassword(password.getBytes("UTF-8"), accessToken);
        }

        if (expires != null) {
            urlShare.setExpires(expires, accessToken);
        }
        sqlTrans.commit();
        return urlShare.getKey();
    }

    protected void mockTime(long t)
    {
        TimeSource timeSource = inj.getInstance(TimeSource.class);
        doReturn(t).when(timeSource).getTime();
    }

    protected void invite(UserID sharer, SID sid, UserID sharee, Permissions p) throws Exception
    {
        sqlTrans.begin();
        factSF.create(sid).addPendingUser(factUser.create(sharee), p, factUser.create(sharer));
        sqlTrans.commit();
    }

    protected void addUser(SID sid, UserID user, Permissions p) throws Exception
    {
        sqlTrans.begin();
        factSF.create(sid).addJoinedUser(factUser.create(user), p);
        sqlTrans.commit();
    }

    protected void addGroup(SID sid, GroupID groupID, Permissions p, UserID sharer) throws Exception
    {
        sqlTrans.begin();
        factGroup.create(groupID).joinSharedFolder(factSF.create(sid), p, factUser.create(sharer));
        sqlTrans.commit();
    }

    protected void addUserToGroup(GroupID group, UserID user) throws Exception
    {
        sqlTrans.begin();
        factGroup.create(group).addMember(factUser.create(user));
        sqlTrans.commit();
    }

    protected String membersEtag(UserID user) throws SQLException
    {
        sqlTrans.begin();
        String etag = "W/\"" + aclEtag(factUser.create(user)) + "\"";
        sqlTrans.commit();
        return etag;
    }

    /** create a User object without adding anything to the db */
    protected User newUser()
    {
        return factUser.create(UserID.fromInternal("u" + Integer.toString(++nextUserID) + "@email"));
    }
}
