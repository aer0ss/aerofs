package com.aerofs.ca.utils;

import com.aerofs.baseline.db.MySQLDatabase;
import com.aerofs.ca.server.TestCAServer;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import java.util.Date;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class TestCertificateSigner
{
    @Rule
    public RuleChain sampleServer = RuleChain.outerRule(new MySQLDatabase("test")).around(new TestCAServer());

    @Test
    public void shouldGenerateUniqueCNs()
            throws Exception
    {
        CertificateSigner cert1 = CertificateSigner.certificateSignerWithKeys(KeyUtils.newKeyPair());
        CertificateSigner cert2 = CertificateSigner.certificateSignerWithKeys(KeyUtils.newKeyPair());
        assertNotEquals(cert1.caCert().getSubject(), cert2.caCert().getSubject());
    }

    @Test
    public void shouldSetStartDateBeforeCurrentTime()
            throws Exception
    {
        CertificateSigner signer = CertificateSigner.certificateSignerWithKeys(KeyUtils.newKeyPair());
        long timeDiff = signer.caCert().getNotBefore().getTime() - new Date().getTime();
        long hoursDiff = TimeUnit.HOURS.convert(timeDiff, TimeUnit.MILLISECONDS);
        // at least 23 hours before current time
        assertTrue("certificate's not before date too recent", hoursDiff <= -23);
    }
}
