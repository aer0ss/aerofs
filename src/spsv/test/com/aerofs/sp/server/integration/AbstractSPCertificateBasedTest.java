/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.integration;

import com.aerofs.base.C;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.UniqueID;
import com.aerofs.base.id.UserID;
import com.aerofs.sp.server.lib.cert.CertificateGenerator.CertificationResult;
import com.aerofs.sp.server.lib.device.Device;
import com.aerofs.sp.server.lib.user.User;
import org.junit.Before;

import java.sql.Timestamp;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A class used to initialize tests related to certificates and certificate revocation.
 *
 * TODO (WW) merge this class with AbstractSPTest
 */
public class AbstractSPCertificateBasedTest extends AbstractSPTest
{
    // Private static finals.
    protected static final String RETURNED_CERT = "returned_cert";
    protected final User TEST_1_USER = factUser.create(UserID.fromInternal("test1@aerofs.com"));
    protected final User TEST_2_USER = factUser.create(UserID.fromInternal("test2@aerofs.com"));

    // Device ID that the tests will work with.
    protected Device device = factDevice.create(new DID(UniqueID.generate()));

    // The serial number we will use for mocking.
    protected static long _lastSerialNumber = 564645L;

    // TODO (WW) remove this method. The stuff in this method should be done by concerete classes
    @Before
    public void setup()
            throws Exception
    {
        l.info("Add test users to sp_user to satisfy foreign key constraints for d_owner_id");
        sqlTrans.begin();
        saveUser(TEST_1_USER);
        saveUser(TEST_2_USER);
        sqlTrans.commit();

        mockCertificateGeneratorAndIncrementSerialNumber();

        setSession(TEST_1_USER);
    }

    @Override
    protected Device saveDevice(User user) throws Exception
    {
        CertificationResult cert = mock(CertificationResult.class);
        when(cert.toString()).thenReturn(AbstractSPCertificateBasedTest.RETURNED_CERT);
        when(cert.getSerial()).thenReturn(++AbstractSPCertificateBasedTest._lastSerialNumber);
        when(cert.getExpiry()).thenReturn(
                new Timestamp(System.currentTimeMillis() + C.DAY * 365L));

        Device d = super.saveDevice(user);
        d.addCertificate(cert);
        return d;
    }

    protected long getLastSerialNumber()
    {
        return _lastSerialNumber;
    }
}
