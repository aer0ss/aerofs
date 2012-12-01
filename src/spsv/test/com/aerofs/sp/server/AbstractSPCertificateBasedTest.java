/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server;

import com.aerofs.lib.id.DID;
import com.aerofs.lib.id.UniqueID;
import com.aerofs.lib.id.UserID;
import org.junit.Before;

/**
 * A class used to initialize tests related to certificates and certificate revocation.
 *
 * TODO (WW) merge this class with AbstractSPServiceTest
 */
public class AbstractSPCertificateBasedTest extends AbstractSPServiceTest
{
    // Private static finals.
    protected static final String RETURNED_CERT = "returned_cert";
    protected static final UserID TEST_1_USER = UserID.fromInternal("test1@aerofs.com");
    protected static final UserID TEST_2_USER = UserID.fromInternal("test2@aerofs.com");

    // Device ID that the tests will work with.
    protected DID _did = new DID(UniqueID.generate());

    // The serial number we will use for mocking.
    protected static long _lastSerialNumber = 564645L;

    // TODO (WW) remove this method. The stuff in this method should be done by concerete classes
    @Before
    public void setup()
            throws Exception
    {
        Log.info("Add test users to sp_user to satisfy foreign key constraints for d_owner_id");
        transaction.begin();
        addTestUser(TEST_1_USER);
        addTestUser(TEST_2_USER);
        transaction.commit();

        mockCertificateGeneratorAndIncrementSerialNumber();

        setSessionUser(TEST_1_USER);
    }

    protected long getLastSerialNumber()
    {
        return _lastSerialNumber;
    }
}
