/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.sparta.resources;

import com.aerofs.audit.client.AuditClient;
import com.aerofs.audit.client.IAuditorClient;
import com.aerofs.base.BaseSecUtil;
import com.aerofs.base.BaseUtil;
import com.aerofs.base.Loggers;
import com.aerofs.base.acl.Permissions;
import com.aerofs.base.config.ConfigurationProperties;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.id.OrganizationID;
import com.aerofs.base.id.SID;
import com.aerofs.base.id.UserID;
import com.aerofs.bifrost.oaaas.model.Client;
import com.aerofs.bifrost.oaaas.model.ResourceServer;
import com.aerofs.bifrost.server.Bifrost;
import com.aerofs.bifrost.server.BifrostTest;
import com.aerofs.lib.FullName;
import com.aerofs.rest.util.OAuthToken;
import com.aerofs.oauth.Scope;
import com.aerofs.servlets.lib.db.LocalTestDatabaseConfigurator;
import com.aerofs.servlets.lib.db.SPDatabaseParams;
import com.aerofs.servlets.lib.db.sql.SQLThreadLocalTransaction;
import com.aerofs.sp.client.SPBlockingClient;
import com.aerofs.sp.server.CommandDispatcher;
import com.aerofs.sp.server.PasswordManagement;
import com.aerofs.sp.server.lib.SharedFolder;
import com.aerofs.sp.server.lib.organization.Organization;
import com.aerofs.sp.server.lib.user.AuthorizationLevel;
import com.aerofs.sp.server.lib.user.User;
import com.aerofs.sp.sparta.Sparta;
import com.aerofs.testlib.AbstractBaseTest;
import com.aerofs.verkehr.client.rest.VerkehrClient;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListenableFuture;
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
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.handler.codec.http.HttpHeaders.Names;
import org.jboss.netty.util.HashedWheelTimer;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.slf4j.Logger;

import java.security.MessageDigest;
import java.sql.SQLException;
import java.util.Properties;

import static com.aerofs.bifrost.server.BifrostTest.createAccessToken;
import static com.aerofs.bifrost.server.BifrostTest.createClient;
import static com.aerofs.bifrost.server.BifrostTest.createResourceServer;
import static com.aerofs.sp.sparta.resources.SharedFolderResource.aclEtag;
import static com.aerofs.sp.sparta.resources.SharedFolderResource.listPendingMembers;
import static com.jayway.restassured.RestAssured.given;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AbstractResourceTest extends AbstractBaseTest
{
    protected static final Logger l = Loggers.getLogger(AbstractResourceTest.class);

    private static SessionFactory sessionFactory;
    private static Session session;
    private static Bifrost bifrost;

    protected Sparta sparta;
    protected Injector inj;

    protected static final UserID user = UserID.fromInternal("user@bar.baz");
    protected static final UserID admin = UserID.fromInternal("admin@bar.baz");
    protected static final UserID other = UserID.fromInternal("other@bar.baz");

    private static final String RO_SELF = "roself";
    private static final String RW_SELF = "rwself";
    private static final String ADMIN = "admin";
    private static final String OTHER = "other";

    @Mock VerkehrClient verkehrClient;
    @Mock CommandDispatcher commandDispatcher;
    @Mock PasswordManagement passwordManagement;

    private final SPDatabaseParams dbParams = new SPDatabaseParams();

    protected SQLThreadLocalTransaction sqlTrans;
    protected User.Factory factUser;
    protected SharedFolder.Factory factSF;
    private int nextUserID = 1;

    @BeforeClass
    public static void commonSetup() throws Exception
    {
        Properties prop = new Properties();
        prop.setProperty("bifrost.port", "0");
        ConfigurationProperties.setProperties(prop);

        sessionFactory = mock(SessionFactory.class);
        session = mock(Session.class);

        when(sessionFactory.openSession()).thenReturn(session);

        // start OAuth service
        bifrost = new Bifrost(bifrostInjector());
        bifrost.start();
        l.info("OAuth service at {}", bifrost.getListeningPort());

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

    private Injector spartaInjector() throws Exception
    {
        return Guice.createInjector(
                Sparta.spartaModule(new HashedWheelTimer(), new NioClientSocketChannelFactory()),
                Sparta.databaseModule(dbParams.getProvider()), new AbstractModule()
        {
            @Override
            protected void configure()
            {
                bind(VerkehrClient.class).toInstance(verkehrClient);
                bind(CommandDispatcher.class).toInstance(commandDispatcher);
                bind(PasswordManagement.class).toInstance(passwordManagement);
                bind(AuditClient.class).toInstance(
                        new AuditClient().setAuditorClient(new IAuditorClient()
                        {
                            @Override
                            public void submit(String content)
                            {
                                l.info("audit: {}", content);
                            }
                        }));
            }
        });
    }

    @Before
    public void setUp() throws Exception
    {
        LocalTestDatabaseConfigurator.initializeLocalDatabase(dbParams);

        when(verkehrClient.publish(anyString(), any(byte[].class)))
                .thenAnswer(new Answer<ListenableFuture<Void>>()
                {
                    @Override
                    public ListenableFuture<Void> answer(InvocationOnMock invocation)
                            throws Throwable
                    {
                        SettableFuture<Void> f = SettableFuture.create();
                        f.set(null);
                        return f;
                    }
                });

        when(verkehrClient.revokeSerials(Matchers.<ImmutableCollection<Long>>anyObject())).thenAnswer(
                new Answer<ListenableFuture<Void>>()
                {
                    @Override
                    public ListenableFuture<Void> answer(InvocationOnMock invocation)
                    {
                        SettableFuture<Void> f = SettableFuture.create();
                        f.set(null);
                        return f;
                    }
                }
        );
        when(commandDispatcher.getVerkehrClient()).thenReturn(verkehrClient);

        inj = spartaInjector();
        sqlTrans = inj.getInstance(SQLThreadLocalTransaction.class);
        factUser = inj.getInstance(User.Factory.class);
        factSF = inj.getInstance(SharedFolder.Factory.class);

        sqlTrans.begin();
        User u = factUser.create(user);
        u.save(new byte[0], new FullName("User", "Foo"));
        User o = factUser.create(other);
        o.save(new byte[0], new FullName("Other", "Foo"));
        User a = factUser.create(admin);
        a.save(new byte[0], new FullName("Admin", "Foo"));

        Organization org = inj.getInstance(Organization.Factory.class)
                .save(OrganizationID.PRIVATE_ORGANIZATION);
        a.setOrganization(org, AuthorizationLevel.ADMIN);
        u.setOrganization(org, AuthorizationLevel.USER);
        o.setOrganization(org, AuthorizationLevel.USER);
        sqlTrans.commit();

        sparta = new Sparta(inj);
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
        MessageDigest md = BaseSecUtil.newMessageDigestMD5();
        listPendingMembers(factSF.create(sid), md);
        String etag = "W/\""
                + aclEtag(factUser.create(user))
                + BaseUtil.hexEncode(md.digest())
                + "\"";
        sqlTrans.commit();
        return etag;
    }

    protected String sharesEtag(UserID user) throws SQLException, ExNotFound
    {
        sqlTrans.begin();
        MessageDigest md = BaseSecUtil.newMessageDigestMD5();
        OAuthToken tok = mock(OAuthToken.class);
        when(tok.hasFolderPermission(any(Scope.class), any(SID.class))).thenReturn(true);
        UsersResource.listShares(factUser.create(user), md, tok);
        String etag = "W/\""
                + aclEtag(factUser.create(user))
                + BaseUtil.hexEncode(md.digest())
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
