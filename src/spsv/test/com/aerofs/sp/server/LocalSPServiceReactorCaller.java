/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server;

import com.aerofs.lib.FullName;
import com.aerofs.lib.SecUtil;
import com.aerofs.lib.ex.ExAlreadyExist;
import com.aerofs.lib.ex.Exceptions;
import com.aerofs.base.id.UserID;
import com.aerofs.proto.Common.PBException;
import com.aerofs.proto.Sp.SPServiceReactor;
import com.aerofs.proto.Sp.SPServiceStub.SPServiceStubCallbacks;
import com.aerofs.servlets.MockSessionUser;
import com.aerofs.servlets.lib.db.SPDatabaseParams;
import com.aerofs.servlets.lib.db.LocalTestDatabaseConfigurator;
import com.aerofs.servlets.lib.db.jedis.JedisEpochCommandQueue;
import com.aerofs.servlets.lib.db.jedis.JedisThreadLocalTransaction;
import com.aerofs.servlets.lib.db.jedis.PooledJedisConnectionProvider;
import com.aerofs.servlets.lib.db.sql.SQLThreadLocalTransaction;
import com.aerofs.sp.server.email.DeviceRegistrationEmailer;
import com.aerofs.sp.server.email.RequestToSignUpEmailer;
import com.aerofs.sp.server.lib.cert.Certificate;
import com.aerofs.sp.server.lib.cert.CertificateDatabase;
import com.aerofs.sp.server.lib.device.Device;
import com.aerofs.sp.server.lib.device.DeviceDatabase;
import com.aerofs.sp.server.lib.*;
import com.aerofs.sp.server.email.InvitationEmailer;
import com.aerofs.sp.server.lib.cert.CertificateGenerator;
import com.aerofs.sp.server.email.PasswordResetEmailer;
import com.aerofs.sp.server.lib.id.OrganizationID;
import com.aerofs.sp.server.lib.organization.Organization;
import com.aerofs.sp.server.lib.organization.OrganizationInvitation;
import com.aerofs.sp.server.lib.session.CertificateAuthenticator;
import com.aerofs.sp.server.lib.session.ThreadLocalHttpSessionProvider;
import com.aerofs.sp.server.lib.user.User;
import com.aerofs.sp.server.lib.user.AuthorizationLevel;
import com.aerofs.sp.server.session.SPActiveUserSessionTracker;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.protobuf.ByteString;

import java.io.IOException;
import java.sql.SQLException;

import static org.mockito.Mockito.mock;

/**
 * A mock/test implementation of SPServiceStubCallbacks.
 *
 * This class instantiates an SPService and Reactor on the local machine,
 * using a LocalTestSPDatabase which sets up a clean mysql db according to sp.sql.
 * Instead of contacting an SP Servlet through doRPC, this implementation sends the payload
 * directly to the local SPServiceReactor.
 */
public class LocalSPServiceReactorCaller implements SPServiceStubCallbacks
{
    private final SPDatabaseParams _dbParams = new SPDatabaseParams();
    private final SQLThreadLocalTransaction sqlTrans =
            new SQLThreadLocalTransaction(_dbParams.getProvider());
    private final SPServiceReactor reactor;

    private final UserDatabase udb = new UserDatabase(sqlTrans);

    // On initialization, a single admin is added to the sp database to enable authenticated calls
    public static final UserID ADMIN_ID = UserID.fromInternal("testadmin@company.com");
    public static final byte [] ADMIN_CRED = SecUtil.scrypt("adminpswd".toCharArray(), ADMIN_ID);

    public LocalSPServiceReactorCaller(InvitationEmailer.Factory factEmailer)
    {
        // Instantiate with a
        // - local mysql db
        // - local JUnitSPDatabaseParams
        // - local ThreadLocalTransaction
        // - mock SessionUserID (no thread-local variables; tests should be single-threaded)
        // - real PasswordManagement class (using a local database).
        // - mock OrganizationManagement class
        // - injected InvitationEmailer

        assert factEmailer != null;

        SPDatabase db = new SPDatabase(sqlTrans);
        DeviceDatabase ddb = new DeviceDatabase(sqlTrans);
        CertificateDatabase certdb = new CertificateDatabase(sqlTrans);
        EmailSubscriptionDatabase esdb = new EmailSubscriptionDatabase(sqlTrans);
        OrganizationDatabase odb = new OrganizationDatabase(sqlTrans);
        SharedFolderDatabase sfdb = new SharedFolderDatabase(sqlTrans);
        OrganizationInvitationDatabase oidb = new OrganizationInvitationDatabase(sqlTrans);

        CertificateGenerator certgen = new CertificateGenerator();

        Organization.Factory factOrg = new Organization.Factory();
        SharedFolder.Factory factSharedFolder = new SharedFolder.Factory();
        Certificate.Factory factCert = new Certificate.Factory(certdb);
        Device.Factory factDevice = new Device.Factory();
        OrganizationInvitation.Factory factOrgInvite = new OrganizationInvitation.Factory();

        User.Factory factUser = new User.Factory(udb, oidb, factDevice, factOrg, factOrgInvite,
                factSharedFolder);
        {
            factDevice.inject(ddb, certdb, certgen, factUser, factCert);
            factOrg.inject(odb, oidb, factUser, factSharedFolder, factOrgInvite);
            factOrgInvite.inject(oidb, factUser, factOrg);
            factSharedFolder.inject(sfdb, factUser);
        }

        PasswordManagement passwordManagement =
                new PasswordManagement(db, factUser, mock(PasswordResetEmailer.class));
        DeviceRegistrationEmailer deviceRegistrationEmailer = mock(DeviceRegistrationEmailer.class);
        RequestToSignUpEmailer requestToSignUpEmailer = mock(RequestToSignUpEmailer.class);

        ThreadLocalHttpSessionProvider sessionProvider = new ThreadLocalHttpSessionProvider();
        CertificateAuthenticator certificateAuthenticator =
                new CertificateAuthenticator(sessionProvider);

        SPActiveUserSessionTracker userTracker = new SPActiveUserSessionTracker();

        PooledJedisConnectionProvider jedisConProvider = new PooledJedisConnectionProvider();
        JedisThreadLocalTransaction jedisTrans = new JedisThreadLocalTransaction(jedisConProvider);
        JedisEpochCommandQueue commandQueue = new JedisEpochCommandQueue(jedisTrans);

        SPService service = new SPService(db, sqlTrans, jedisTrans, new MockSessionUser(),
                passwordManagement, certificateAuthenticator, factUser, factOrg, factOrgInvite,
                factDevice, factCert, certdb, esdb, factSharedFolder, factEmailer,
                deviceRegistrationEmailer, requestToSignUpEmailer, commandQueue);

        service.setUserTracker(userTracker);
        reactor = new SPServiceReactor(service);
    }

    /**
     * Ensure you call this in your test @Before code!
     */
    public void init_()
            throws IOException, ClassNotFoundException, SQLException, InterruptedException,
                ExAlreadyExist
    {
        // Database setup.
        LocalTestDatabaseConfigurator.initializeLocalDatabase(_dbParams);

        byte[] cred = com.aerofs.sp.server.lib.SPParam.getShaedSP(ByteString.copyFrom(ADMIN_CRED)
                .toByteArray());

        // Add an admin to the db so that authenticated calls can be performed on the SPService
        sqlTrans.begin();
        udb.insertUser(ADMIN_ID, new FullName("first", "last"), cred, OrganizationID.DEFAULT,
                AuthorizationLevel.ADMIN);
        sqlTrans.commit();
    }

    @Override
    public ListenableFuture<byte[]> doRPC(byte[] data)
    {
        return reactor.react(data);
    }

    @Override
    public Throwable decodeError(PBException error)
    {
        return Exceptions.fromPB(error);
    }
}
