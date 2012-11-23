/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server;

import com.aerofs.lib.SecUtil;
import com.aerofs.lib.ex.ExAlreadyExist;
import com.aerofs.lib.ex.Exceptions;
import com.aerofs.proto.Common.PBException;
import com.aerofs.proto.Sp.SPServiceReactor;
import com.aerofs.proto.Sp.SPServiceStub.SPServiceStubCallbacks;
import com.aerofs.servlets.MockSessionUserID;
import com.aerofs.servlets.lib.db.SPDatabaseParams;
import com.aerofs.servlets.lib.db.LocalTestDatabaseConfigurator;
import com.aerofs.servlets.lib.db.SQLThreadLocalTransaction;
import com.aerofs.sp.server.lib.SPDatabase;
import com.aerofs.sp.server.email.InvitationEmailer;
import com.aerofs.sp.server.cert.CertificateGenerator;
import com.aerofs.sp.server.email.PasswordResetEmailer;
import com.aerofs.sp.server.lib.organization.OrgID;
import com.aerofs.sp.server.organization.OrganizationManagement;
import com.aerofs.sp.server.user.UserManagement;
import com.aerofs.sp.server.lib.user.AuthorizationLevel;
import com.aerofs.sp.server.lib.user.User;
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
    private final SQLThreadLocalTransaction _transaction =
            new SQLThreadLocalTransaction(_dbParams.getProvider());
    private final SPDatabase _db = new SPDatabase(_transaction);
    private final SPServiceReactor _reactor;

    // On initialization, a single admin is added to the sp database to enable authenticated calls
    public static final String ADMIN_ID = "testadmin@company.com";
    public static final byte [] ADMIN_CRED = SecUtil.scrypt("adminpswd".toCharArray(), ADMIN_ID);

    public LocalSPServiceReactorCaller(InvitationEmailer.Factory emailerFactory)
    {
        // Instantiate with a
        // - local mysql db
        // - local JUnitSPDatabaseParams
        // - local ThreadLocalTransaction
        // - mock SessionUserID (no thread-local variables; tests should be single-threaded)
        // - real UserManagement class (using a local database).
        // - mock OrganizationManagement class
        // - injected InvitationEmailer

        assert emailerFactory != null;

        UserManagement userManagement =
                new UserManagement(_db, _db, emailerFactory, mock(PasswordResetEmailer.class));
        OrganizationManagement organizationManagement = mock(OrganizationManagement.class);

        SPService service = new SPService(_db, _transaction, new MockSessionUserID(),
                userManagement, organizationManagement,
                new SharedFolderManagement(_db, userManagement, organizationManagement,
                        emailerFactory),
                new CertificateGenerator());

        _reactor = new SPServiceReactor(service);
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

        // Add an admin to the db so that authenticated calls can be performed on the SPService
        _transaction.begin();
        _db.addUser(new User(ADMIN_ID, "first", "last", ByteString.copyFrom(ADMIN_CRED),
                false, OrgID.DEFAULT, AuthorizationLevel.ADMIN));
        _db.markUserVerified(ADMIN_ID);
        _transaction.commit();
    }

    @Override
    public ListenableFuture<byte[]> doRPC(byte[] data)
    {
        return _reactor.react(data);
    }

    @Override
    public Throwable decodeError(PBException error)
    {
        return Exceptions.fromPB(error);
    }
}
