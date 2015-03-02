/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport;

import com.aerofs.testlib.SecTestUtil;

import java.security.KeyPair;
import java.security.SecureRandom;

public final class MockCA
{
    private final String caName;
    private final KeyPair caKeyPair;
    private final CACertificateProvider caCertificateProvider;

    public MockCA(String caName, SecureRandom secureRandom)
            throws Exception
    {
        this.caName = caName;
        this.caKeyPair = SecTestUtil.generateKeyPairNoCheckedThrow(secureRandom);
        this.caCertificateProvider = new CACertificateProvider(secureRandom, caName, caKeyPair);
    }

    public String getCaName()
    {
        return caName;
    }

    public KeyPair getCaKeyPair()
    {
        return caKeyPair;
    }

    public CACertificateProvider getCACertificateProvider()
    {
        return caCertificateProvider;
    }
}
