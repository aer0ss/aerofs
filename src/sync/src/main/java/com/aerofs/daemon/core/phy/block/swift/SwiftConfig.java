package com.aerofs.daemon.core.phy.block.swift;

import com.aerofs.base.C;
import com.aerofs.daemon.core.phy.block.encrypted.BackendConfig;
import org.javaswift.joss.client.factory.AccountConfig;
import org.javaswift.joss.client.factory.AccountFactory;
import org.javaswift.joss.client.factory.AuthenticationMethod;
import org.javaswift.joss.model.Account;

public class SwiftConfig extends BackendConfig
{
    private final String _username;
    private final String _password;
    private final String _url;
    private final AuthenticationMethod _authMethod;
    private final String _containerName;

    // Timeout
    private static final Long CONNECTION_TIMEOUT = 10*C.SEC;

    public SwiftConfig(String username, String password, String url, AuthenticationMethod authMethod, String containerName)
    {
        _username = username;
        _password = password;
        _url = url;
        _authMethod = authMethod;
        _containerName = containerName;
    }

    /**
     * Return a JOSS Account
     * It contains credentials and connection information
     *
     * @return the Account object
     */
    public Account getAccount()
    {
        AccountConfig config = getAccountConfig();
        return new AccountFactory(config).createAccount();
    }

    protected AccountConfig getAccountConfig()
    {
        AccountConfig config = new AccountConfig();
        config.setUsername(_username);
        config.setPassword(_password);
        config.setAuthUrl(_url);
        config.setAuthenticationMethod(_authMethod);
        config.setSocketTimeout(CONNECTION_TIMEOUT.intValue());
        return config;
    }

    public String getContainerName()
    {
        return _containerName;
    }
}
