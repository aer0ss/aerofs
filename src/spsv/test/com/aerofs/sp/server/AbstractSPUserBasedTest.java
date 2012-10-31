/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server;

import com.aerofs.lib.C;
import com.aerofs.lib.async.UncancellableFuture;
import com.aerofs.sp.server.lib.user.AuthorizationLevel;
import com.aerofs.sp.server.lib.user.User;
import com.aerofs.proto.Common.Void;
import com.aerofs.sp.server.email.InvitationEmailer;
import com.aerofs.sp.server.organization.OrganizationManagement;
import com.aerofs.sp.server.user.UserManagement;
import org.junit.Before;
import org.mockito.Spy;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A class used to initialize tests that require users to be set up in the database.
 */
public class AbstractSPUserBasedTest extends AbstractSPServiceTest
{
    // Mock invitation emailer for use with sp.shareFolder calls
    protected final InvitationEmailer.Factory emailerFactory = mock(InvitationEmailer.Factory.class);

    @Spy UserManagement _userManagement = new UserManagement(db, db, emailerFactory, null);
    @Spy OrganizationManagement _organizationManagement =
            new OrganizationManagement(db, _userManagement);
    @Spy SharedFolderManagement _sharedFolderManagement = new SharedFolderManagement(db,
            _userManagement, _organizationManagement, emailerFactory);


    protected static final String TEST_USER_1_NAME = "USER_1";
    protected static final byte[] TEST_USER_1_CRED = "CREDENTIALS".getBytes();

    protected static final String TEST_USER_2_NAME = "USER_2";
    protected static final byte[] TEST_USER_2_CRED = "CREDENTIALS".getBytes();

    protected static final String TEST_USER_3_NAME = "USER_3";
    protected static final byte[] TEST_USER_3_CRED = "CREDENTIALS".getBytes();

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
        when(emailerFactory.createUserInvitation(anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString())).thenReturn(new InvitationEmailer());
        when(emailerFactory.createFolderInvitation(anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString())).thenReturn(new InvitationEmailer());

        final boolean finalized = true;
        final boolean verified = false; // This field doesn't matter.
        String orgId = C.DEFAULT_ORGANIZATION;
        AuthorizationLevel level = AuthorizationLevel.USER;

        // Add all the users to the db.
        _transaction.begin();
        db.addUser(new User(TEST_USER_1_NAME, TEST_USER_1_NAME, TEST_USER_1_NAME, TEST_USER_1_CRED,
                finalized, verified, orgId, level));
        db.markUserVerified(TEST_USER_1_NAME);
        db.addUser(new User(TEST_USER_2_NAME, TEST_USER_2_NAME, TEST_USER_2_NAME, TEST_USER_2_CRED,
                finalized, verified, orgId, level));
        db.markUserVerified(TEST_USER_2_NAME);
        db.addUser(new User(TEST_USER_3_NAME, TEST_USER_3_NAME, TEST_USER_3_NAME, TEST_USER_3_CRED,
                finalized, verified, orgId, level));
        db.markUserVerified(TEST_USER_3_NAME);
        _transaction.commit();
    }
}
