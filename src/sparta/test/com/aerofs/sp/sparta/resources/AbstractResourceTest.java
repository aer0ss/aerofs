/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.sparta.resources;

import com.aerofs.audit.client.AuditClient;
import com.aerofs.base.ElapsedTimer;
import com.aerofs.base.Loggers;
import com.aerofs.base.acl.Permissions;
import com.aerofs.base.config.ConfigurationProperties;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.id.GroupID;
import com.aerofs.base.id.OrganizationID;
import com.aerofs.ids.DID;
import com.aerofs.ids.SID;
import com.aerofs.ids.UserID;
import com.aerofs.bifrost.oaaas.model.Client;
import com.aerofs.bifrost.oaaas.model.ResourceServer;
import com.aerofs.bifrost.server.Bifrost;
import com.aerofs.bifrost.server.BifrostTest;
import com.aerofs.lib.FullName;
import com.aerofs.rest.auth.OAuthToken;
import com.aerofs.oauth.Scope;
import com.aerofs.servlets.lib.db.BifrostDatabaseParams;
import com.aerofs.servlets.lib.db.LocalTestDatabaseConfigurator;
import com.aerofs.servlets.lib.db.SPDatabaseParams;
import com.aerofs.servlets.lib.db.sql.SQLThreadLocalTransaction;
import com.aerofs.sp.client.SPBlockingClient;
import com.aerofs.sp.server.ACLNotificationPublisher;
import com.aerofs.sp.server.CommandDispatcher;
import com.aerofs.sp.server.PasswordManagement;
import com.aerofs.sp.server.lib.License;
import com.aerofs.sp.server.lib.device.Device;
import com.aerofs.sp.server.lib.group.Group;
import com.aerofs.sp.server.lib.sf.SharedFolder;
import com.aerofs.sp.server.lib.organization.Organization;
import com.aerofs.sp.server.lib.user.AuthorizationLevel;
import com.aerofs.sp.server.lib.user.User;
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
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
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
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.handler.codec.http.HttpHeaders.Names;
import org.jboss.netty.util.HashedWheelTimer;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.mockito.Mock;
import org.slf4j.Logger;

import java.sql.SQLException;
import java.sql.Statement;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;
import java.util.TimeZone;

import static com.aerofs.bifrost.server.BifrostTest.createAccessToken;
import static com.aerofs.bifrost.server.BifrostTest.createClient;
import static com.aerofs.bifrost.server.BifrostTest.createResourceServer;
import static com.aerofs.sp.sparta.resources.SharedFolderResource.aclEtag;
import static com.aerofs.sp.sparta.resources.SharedFolderResource.listPendingMembers;
import static com.jayway.restassured.RestAssured.given;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AbstractResourceTest extends AbstractBaseTest
{
    protected static final Logger l = Loggers.getLogger(AbstractResourceTest.class);

    private static SessionFactory sessionFactory;
    private static Session session;
    private static Bifrost bifrost;
    protected static String deploymentSecret = "81706d9d9cbdbc4e6f14e08117cfcd73";

    protected Sparta sparta;
    protected Injector inj;

    protected static final UserID user = UserID.fromInternal("user@bar.baz");
    protected static final UserID admin = UserID.fromInternal("admin@bar.baz");
    protected static final UserID other = UserID.fromInternal("other@bar.baz");

    private static final String RO_SELF = "roself";
    private static final String RW_SELF = "rwself";
    private static final String ADMIN = "admin";
    private static final String OTHER = "other";

    private static final SSMPConnection ssmp = mock(SSMPConnection.class);
    private static final CommandDispatcher commandDispatcher = mock(CommandDispatcher.class);
    protected static final PasswordManagement passwordManagement = mock(PasswordManagement.class);
    protected static final License license = mock(License.class);
    @Mock ACLNotificationPublisher aclNotificationPublisher;
    @Mock AuditClient auditClient;

    private static final SPDatabaseParams dbParams = new SPDatabaseParams();

    protected SQLThreadLocalTransaction sqlTrans;
    protected User.Factory factUser;
    protected SharedFolder.Factory factSF;
    protected Group.Factory factGroup;
    protected Device.Factory factDevice;
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

        sessionFactory = mock(SessionFactory.class);
        session = mock(Session.class);

        when(sessionFactory.openSession()).thenReturn(session);

        when(ssmp.request(any(SSMPRequest.class)))
                .thenAnswer(invocation -> {
                    SettableFuture<SSMPResponse> f = SettableFuture.create();
                    f.set(new SSMPResponse(SSMPResponse.OK, ""));
                    return f;
                });

        ElapsedTimer t = new ElapsedTimer();

        // start OAuth service
        bifrost = new Bifrost(bifrostInjector(), deploymentSecret);
        bifrost.start();
        l.info("OAuth service at {}", bifrost.getListeningPort());

        System.out.println("started bifrost in " + t.elapsed());

        String bifrostUrl =
                "http://localhost:" + bifrost.getListeningPort() + "/tokeninfo";

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
            }
        });

        ResourceServer rs = createResourceServer(inj);
        Client client = createClient(inj, rs, BifrostTest.CLIENTID, BifrostTest.CLIENTSECRET,
                BifrostTest.CLIENTNAME, ImmutableSet.of("files.read", "files.write"), 0L);

        createAccessToken(client, inj, RW_SELF, user, OrganizationID.PRIVATE_ORGANIZATION, 0,
                ImmutableSet.of("user.read", "acl.read",
                        "user.write", "acl.write", "acl.invitations"));
        createAccessToken(client, inj, RO_SELF, user, OrganizationID.PRIVATE_ORGANIZATION, 0,
                ImmutableSet.of("user.read", "acl.read", "acl.invitations"));
        createAccessToken(client, inj, ADMIN, OrganizationID.PRIVATE_ORGANIZATION.toTeamServerUserID(), OrganizationID.PRIVATE_ORGANIZATION, 0,
                ImmutableSet.of("user.read", "acl.read",
                        "user.write", "user.password", "acl.write", "acl.invitations"));
        createAccessToken(client, inj, OTHER, other, OrganizationID.PRIVATE_ORGANIZATION, 0,
                ImmutableSet.of("user.read", "acl.read", "user.write", "acl.write", "acl.invitations"));

        return inj;
    }

    private static Injector spartaInjector() throws Exception
    {
        return Guice.createInjector(
                Sparta.spartaModule(new HashedWheelTimer(), new NioClientSocketChannelFactory()),
                Sparta.databaseModule(dbParams.getProvider()), new AbstractModule()
        {
            @Override
            protected void configure()
            {
                bind(SSMPConnection.class).toInstance(ssmp);
                bind(CommandDispatcher.class).toInstance(commandDispatcher);
                bind(PasswordManagement.class).toInstance(passwordManagement);
                bind(AuditClient.class).toInstance(
                        new AuditClient().setAuditorClient(content -> l.info("audit: {}", content)));
                bind(License.class).toInstance(license);
            }
        });
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

        sqlTrans.begin();
        try (Statement s = sqlTrans.getConnection().createStatement()) {
            for (String table : SPDatabaseParams.TABLES) {
                s.execute("delete from sp_" + table);
            }
        }
        Organization org = inj.getInstance(Organization.Factory.class)
                .save(OrganizationID.PRIVATE_ORGANIZATION);

        User u = factUser.create(user);
        u.save(new byte[0], new FullName("User", "Foo"));
        User o = factUser.create(other);
        o.save(new byte[0], new FullName("Other", "Foo"));
        User a = factUser.create(admin);
        a.save(new byte[0], new FullName("Admin", "Foo"));

        a.setOrganization(org, AuthorizationLevel.ADMIN);
        u.setOrganization(org, AuthorizationLevel.USER);
        o.setOrganization(org, AuthorizationLevel.USER);
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

        sparta.stop();
    }

    protected RequestSpecification givenAccess(String token)
    {
        return given()
                .header(Names.AUTHORIZATION, "Bearer " + token);
    }

    protected RequestSpecification givenReadAccess() { return givenAccess(RO_SELF); }
    protected RequestSpecification givenWriteAccess() { return givenAccess(RW_SELF); }
    protected RequestSpecification givenAdminAccess() { return givenAccess(ADMIN); }
    protected RequestSpecification givenOtherAccess() { return givenAccess(OTHER); }

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

    protected SID mkShare(String name, String owner) throws Exception
    {
        SID sid = SID.generate();
        sqlTrans.begin();
        User user = factUser.create(UserID.fromInternal(owner));
        factSF.create(sid).save(name, user);
        sqlTrans.commit();
        return sid;
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

    protected void addGroup(SID sid, GroupID groupID, Permissions p) throws Exception
    {
        sqlTrans.begin();
        factGroup.create(groupID).joinSharedFolder(factSF.create(sid), p, null);
        sqlTrans.commit();
    }

    protected void addUserToGroup(GroupID group, User user) throws Exception
    {
        sqlTrans.begin();
        factGroup.create(group).addMember(user);
        sqlTrans.commit();
    }

    protected String membersEtag(UserID user) throws SQLException
    {
        sqlTrans.begin();
        String etag = "W/\"" + aclEtag(factUser.create(user)) + "\"";
        sqlTrans.commit();
        return etag;
    }

    protected String shareEtag(UserID user, SID sid) throws SQLException, ExNotFound
    {
        sqlTrans.begin();
        listPendingMembers(factSF.create(sid));
        String etag = "W/\""
                + aclEtag(factUser.create(user))
                + "\"";
        sqlTrans.commit();
        return etag;
    }

    protected String sharesEtag(UserID user) throws SQLException, ExNotFound
    {
        sqlTrans.begin();
        OAuthToken tok = mock(OAuthToken.class);
        when(tok.hasFolderPermission(any(Scope.class), any(SID.class))).thenReturn(true);
        UsersResource.listShares(factUser.create(user), tok);
        String etag = "W/\""
                + aclEtag(factUser.create(user))
                + "\"";
        sqlTrans.commit();
        return etag;
    }

    /** create a User object without adding anything to the db */
    protected User newUser()
    {
        return factUser.create(UserID.fromInternal("u" + Integer.toString(++nextUserID) + "@email"));
    }
}
