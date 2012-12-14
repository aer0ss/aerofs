/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.base;

import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;

import java.io.InputStream;
import java.io.OutputStream;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;

public class TestPemDecoder
{
    static final Logger LOGGER = Loggers.getLogger(TestPemDecoder.class);

    @Ignore
    @Test
    public void testPemInputStream() throws Exception
    {
        OutputStream out = System.out;
        InputStream in = TestPemDecoder.class.getResourceAsStream("cacert_staging.pem");
        in = PemDecoder.decode(in);
        try {
            int ch;
            while ((ch = in.read()) >= 0) {
                out.write(ch);
            }
        } finally {
            in.close();
        }
    }

    @Test
    public void testCertificate() throws Exception
    {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        Certificate certificate;
        InputStream in = TestPemDecoder.class.getResourceAsStream("cacert_staging.pem");
        in = PemDecoder.decode(in);
        try {
            certificate = cf.generateCertificate(in);
        } finally {
            in.close();
        }

        LOGGER.info("{}", certificate);
    }
}
