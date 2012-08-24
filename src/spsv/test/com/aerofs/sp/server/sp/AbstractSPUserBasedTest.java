/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.sp;

import com.aerofs.lib.C;
import com.aerofs.lib.async.UncancellableFuture;
import com.aerofs.servletlib.sp.user.AuthorizationLevel;
import com.aerofs.servletlib.sp.user.User;
import com.aerofs.proto.Common.Void;
import org.junit.Before;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

/**
 * A class used to initialize tests that require users to be set up in the database.
 */
public class AbstractSPUserBasedTest extends AbstractSPServiceTest
{
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
        Log.info("Setting up sp database");
        db.init_();
        Log.info("Add a few users to the database");

        final boolean finalized = true;
        final boolean verified = false; // This field doesn't matter.
        String orgId = C.DEFAULT_ORGANIZATION;
        AuthorizationLevel level = AuthorizationLevel.USER;

        // Add all the users to the db.
        db.addUser(new User(TEST_USER_1_NAME, TEST_USER_1_NAME, TEST_USER_1_NAME, TEST_USER_1_CRED,
                finalized, verified, orgId, level), true);
        db.addUser(new User(TEST_USER_2_NAME, TEST_USER_2_NAME, TEST_USER_2_NAME, TEST_USER_2_CRED,
                finalized, verified, orgId, level), true);
        db.addUser(new User(TEST_USER_3_NAME, TEST_USER_3_NAME, TEST_USER_3_NAME, TEST_USER_3_CRED,
                finalized, verified, orgId, level), true);
    }
}
