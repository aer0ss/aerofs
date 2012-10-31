/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server;

import com.aerofs.lib.OutArg;
import com.aerofs.lib.SecUtil;
import com.aerofs.lib.id.DID;
import com.aerofs.lib.id.UniqueID;
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
import static org.mockito.Matchers.anyString;
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

    // Private static finals.
    protected static final String RETURNED_CERT = "returned_cert";
    protected static final String TEST_1_USER = "test1@aerofs.com";
    protected static final String TEST_2_USER = "test2@aerofs.com";

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

        // Just stub out the certificate generator. Make sure it doesn't try to contact the CA.
        when(certificateGenerator.createCertificate(anyString(), any(DID.class),
                any(PKCS10.class))).thenReturn(certificate);

        when(certificate.toString()).thenReturn(RETURNED_CERT);
        when(certificate.getSerial()).thenReturn(++_lastSerialNumber);

        // Just need some time in the future - say, one year.
        when(certificate.getExpireTs()).thenReturn(new Timestamp(System.currentTimeMillis() +
                1000L*60L*60L*24L*365L));

        // Set up test user and create pub/priv key pair.
        sessionUser.setUser(TEST_1_USER);

        OutArg<PublicKey> publicKey = new OutArg<PublicKey>();
        OutArg<PrivateKey> privateKey = new OutArg<PrivateKey>();
        SecUtil.newRSAKeyPair(publicKey, privateKey);

        _publicKey = publicKey.get();
        _privateKey = privateKey.get();
    }

    protected long getLastSerialNumber()
    {
        return _lastSerialNumber;
    }
}
