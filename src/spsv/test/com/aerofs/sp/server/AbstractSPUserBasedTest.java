/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server;

import com.aerofs.lib.FullName;
import com.aerofs.lib.SecUtil;
import com.aerofs.lib.async.UncancellableFuture;
import com.aerofs.lib.ex.ExAlreadyExist;
import com.aerofs.lib.id.UserID;
import com.aerofs.servlets.MockSessionUser;
import com.aerofs.sp.server.lib.OrganizationDatabase;
import com.aerofs.sp.server.lib.UserDatabase;
import com.aerofs.sp.server.lib.organization.OrgID;
import com.aerofs.sp.server.lib.organization.Organization;
import com.aerofs.sp.server.lib.user.AuthorizationLevel;
import com.aerofs.sp.server.lib.user.User;
import com.aerofs.proto.Common.Void;
import com.aerofs.sp.server.email.InvitationEmailer;
import com.aerofs.sp.server.lib.user.User.Factory;
import com.aerofs.sp.server.organization.OrganizationManagement;
import com.aerofs.sp.server.user.UserManagement;
import org.junit.Before;
import org.mockito.Spy;

import java.sql.SQLException;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A class used to initialize tests that require users to be set up in the database.
 */
public class AbstractSPUserBasedTest extends AbstractSPServiceTest
{
    // To simulate service.signIn(USER, PASSWORD), subclasses can call setSessionUser(UserID)
    @Spy protected MockSessionUser sessionUser;

    // Mock invitation emailer for use with sp.shareFolder calls
    protected final InvitationEmailer.Factory factEmailer = mock(InvitationEmailer.Factory.class);

    protected final OrganizationDatabase odb = new OrganizationDatabase(transaction);
    protected final Organization.Factory factOrg = new Organization.Factory(odb);

    protected final UserDatabase udb = new UserDatabase(transaction);
    protected final User.Factory factUser = new Factory(udb, factOrg);

    @Spy UserManagement userManagement = new UserManagement(db, factUser, factEmailer, null);
    @Spy OrganizationManagement organizationManagement =
            new OrganizationManagement(db);
    @Spy SharedFolderManagement sharedFolderManagement = new SharedFolderManagement(db,
            userManagement, factEmailer, factUser, factOrg);

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

    // Use a method name that is unlikely to conflict with setup methods in subclasses
    @Before
    public void setupAbstractSPUserBasedTest()
            throws Exception
    {
        Log.info("Add a few users to the database");

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
}
