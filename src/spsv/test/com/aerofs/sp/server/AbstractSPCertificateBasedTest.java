/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server;

import com.aerofs.lib.OutArg;
import com.aerofs.lib.SecUtil;
import com.aerofs.lib.id.DID;
import com.aerofs.lib.id.UniqueID;
import com.aerofs.lib.id.UserID;
import com.aerofs.sp.server.lib.user.User;
import com.aerofs.sp.server.cert.Certificate;
import com.aerofs.sp.server.cert.ICertificateGenerator;
import org.junit.Before;
import org.mockito.Mock;
import sun.security.pkcs.PKCS10;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.sql.Timestamp;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

/**
 * A class used to initialize tests related to certificates and certificate revocation.
 */
public class AbstractSPCertificateBasedTest extends AbstractSPServiceTest
{
    // Inject a certificate generator.
    @Mock ICertificateGenerator certificateGenerator;

    // And inject a certificate as well.
    @Mock Certificate certificate;

    // Add inject to a second certificate.
    @Mock Certificate certificate2;

    // Private static finals.
    protected static final String RETURNED_CERT = "returned_cert";
    protected static final UserID TEST_1_USER = UserID.fromInternal("test1@aerofs.com");
    protected static final UserID TEST_2_USER = UserID.fromInternal("test2@aerofs.com");

    // Device ID that the tests will work with.
    protected DID _did = new DID(UniqueID.generate());
    // Private key for the tests.
    protected PrivateKey _privateKey;
    // Public key for the tests.
    protected PublicKey _publicKey;

    // The serial number we will use for mocking.
    protected static long _lastSerialNumber = 564645L;

    @Before
    public void setup()
            throws Exception
    {
        Log.info("Add test users to sp_user to satisfy foreign key constraints for d_owner_id");
        _transaction.begin();
        db.addUser(User.createMockForID(TEST_1_USER));
        db.addUser(User.createMockForID(TEST_2_USER));
        _transaction.commit();

        mockCertificate(certificate);

        // Set up test user and create pub/priv key pair.
        sessionUser.set(TEST_1_USER);

        OutArg<PublicKey> publicKey = new OutArg<PublicKey>();
        OutArg<PrivateKey> privateKey = new OutArg<PrivateKey>();
        SecUtil.newRSAKeyPair(publicKey, privateKey);

        _publicKey = publicKey.get();
        _privateKey = privateKey.get();
    }

    public void mockCertificate(Certificate cert) throws Exception
    {
        // Just stub out the certificate generator. Make sure it doesn't try to contact the CA.
        when(certificateGenerator.createCertificate(any(UserID.class), any(DID.class),
                any(PKCS10.class))).thenReturn(cert);

        when(cert.toString()).thenReturn(RETURNED_CERT);
        when(cert.getSerial()).thenReturn(++_lastSerialNumber);

        // Just need some time in the future - say, one year.
        when(cert.getExpireTs()).thenReturn(new Timestamp(System.currentTimeMillis() +
                1000L*60L*60L*24L*365L));
    }

    protected long getLastSerialNumber()
    {
        return _lastSerialNumber;
    }
}
