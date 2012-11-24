/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server;

import com.aerofs.lib.FullName;
import com.aerofs.lib.SecUtil;
import com.aerofs.lib.ex.ExAlreadyExist;
import com.aerofs.lib.ex.Exceptions;
import com.aerofs.lib.id.UserID;
import com.aerofs.proto.Common.PBException;
import com.aerofs.proto.Sp.SPServiceReactor;
import com.aerofs.proto.Sp.SPServiceStub.SPServiceStubCallbacks;
import com.aerofs.servlets.MockSessionUser;
import com.aerofs.servlets.lib.db.SPDatabaseParams;
import com.aerofs.servlets.lib.db.LocalTestDatabaseConfigurator;
import com.aerofs.servlets.lib.db.SQLThreadLocalTransaction;
import com.aerofs.sp.server.lib.*;
import com.aerofs.sp.server.email.InvitationEmailer;
import com.aerofs.sp.server.cert.CertificateGenerator;
import com.aerofs.sp.server.email.PasswordResetEmailer;
import com.aerofs.sp.server.lib.organization.OrgID;
import com.aerofs.sp.server.lib.user.User.Factory;
import com.aerofs.sp.server.organization.OrganizationManagement;
import com.aerofs.sp.server.user.UserManagement;
import com.aerofs.sp.server.lib.user.AuthorizationLevel;
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
    private final UserDatabase _udb = new UserDatabase(_transaction);
    private final SPServiceReactor _reactor;

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
        // - real UserManagement class (using a local database).
        // - mock OrganizationManagement class
        // - injected InvitationEmailer

        assert factEmailer != null;

        Factory factUser = new Factory(_udb);
        SPDatabase db = new SPDatabase(_transaction);
        UserManagement userManagement =
                new UserManagement(db, db, factUser, factEmailer,
                        mock(PasswordResetEmailer.class));
        OrganizationManagement organizationManagement = mock(OrganizationManagement.class);

        SPService service = new SPService(db, _transaction, new MockSessionUser(),
                userManagement, organizationManagement,
                new SharedFolderManagement(db, userManagement, organizationManagement,
                        factEmailer, factUser),
                new CertificateGenerator(), factUser);

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

        byte[] cred = com.aerofs.sp.server.lib.SPParam.getShaedSP(ByteString.copyFrom(ADMIN_CRED)
                .toByteArray());

        // Add an admin to the db so that authenticated calls can be performed on the SPService
        _transaction.begin();
        _udb.addUser(ADMIN_ID, new FullName("first", "last"), cred, OrgID.DEFAULT,
                AuthorizationLevel.ADMIN);
        _udb.setVerified(ADMIN_ID);
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
