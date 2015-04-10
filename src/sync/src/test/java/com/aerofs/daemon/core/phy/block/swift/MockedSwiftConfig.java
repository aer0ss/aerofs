package com.aerofs.daemon.core.phy.block.swift;

import org.javaswift.joss.client.factory.AccountConfig;
import org.javaswift.joss.client.factory.AccountFactory;
import org.javaswift.joss.client.factory.AuthenticationMethod;
import org.javaswift.joss.model.Account;

class MockedSwiftConfig extends SwiftConfig
{
    public MockedSwiftConfig(String username, String password, String url, AuthenticationMethod authMethod,
                             String containerName, String tenantId, String tenantName) {
        super(username, password, url, authMethod, containerName, tenantId, tenantName);
    }

    public AccountConfig getAccountConfig()
    {
        AccountConfig config = super.getAccountConfig();
        config.setMock(true);
        return config;
    }
}
