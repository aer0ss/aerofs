package com.aerofs.daemon.core.phy.block.swift;

import org.javaswift.joss.client.factory.AuthenticationMethod;

public class SwiftTestConfig
{
    private SwiftConfig.EncryptionPasswordConfig _encryptionPasswordConfig =
            new SwiftConfig.EncryptionPasswordConfig() {
        @Override
        public char[] getPassword()
        {
            return "password".toCharArray();
        }
    };

    public SwiftConfig getSwiftConfig()
    {
        return new MockedSwiftConfig(
                "test:tester",
                "testing",
                "http://192.168.33.10:8080/auth/v1.0",
                AuthenticationMethod.BASIC,
                "container_test"
        );
    }

    public SwiftConfig.EncryptionPasswordConfig getEncryptionPasswordConfig()
    {
        return _encryptionPasswordConfig;
    }

    private SwiftConfig.CryptoConfig _cryptoConfig;
    {
        _cryptoConfig = new SwiftConfig.CryptoConfig(_encryptionPasswordConfig);
    }

    public SwiftConfig.CryptoConfig getCryptoConfig()
    {
        return _cryptoConfig;
    }

}
