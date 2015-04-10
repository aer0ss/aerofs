package com.aerofs.gui.multiuser.setup;

import com.aerofs.base.C;
import com.aerofs.controller.SetupModel;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.HttpClientBuilder;
import org.javaswift.joss.client.factory.AccountConfig;
import org.javaswift.joss.client.factory.AccountFactory;
import org.javaswift.joss.client.factory.AuthenticationMethod;
import org.javaswift.joss.command.shared.identity.tenant.Tenant;
import org.javaswift.joss.command.shared.identity.tenant.Tenants;
import org.javaswift.joss.exception.UnauthorizedException;
import org.javaswift.joss.model.Account;
import org.javaswift.joss.model.Container;

import java.util.ArrayList;
import java.util.Collection;


/**
 * We want to tell the user his credentials are wrong as soon as possible in the process,
 *  so we check the connection (read access only) just after we received the information.
 *
 * We have specific needs, for example the timeout value shouldn't be too high (here 10s).
 * We do not use the existing SwiftBackend from src_sync as this would require a dependency to
 *  the external module.
 */
public class SwiftConnectionChecker {

    protected SetupModel _model;
    protected Account account;

    // Timeout when checking the connection
    public static final Long CONNECTION_TIMEOUT = 10* C.SEC;

    public SwiftConnectionChecker(SetupModel model)
    {
        _model = model;
    }

    /**
     * This will check the connection to the Swift backend and raise an exception if there is a problem.
     * @throws Exception
     */
    public void checkConnection() throws Exception
    {
        AccountConfig config = new AccountConfig();
        config.setUsername(_model._backendConfig._swiftConfig._username);
        config.setPassword(_model._backendConfig._swiftConfig._password);
        config.setAuthUrl(_model._backendConfig._swiftConfig._url);

        switch (_model._backendConfig._swiftConfig._authMode) {
            case "basic": config.setAuthenticationMethod(AuthenticationMethod.BASIC); break;
            case "keystone": config.setAuthenticationMethod(AuthenticationMethod.KEYSTONE); break;
        }

        account = new AccountFactory(config)
                .setHttpClient(buildHttpClient())
                .createAccount();
    }

    /**
     * This will return the list of available tenants, if available.
     *
     * @return Tenants
     * @throws Exception
     */
    public Tenants listTenants() throws Exception
    {
        if (account == null) {
            checkConnection();
        }

        return account.getTenants();
    }

    /**
     * This will return the list of available containers.
     * @throws Exception
     */
    public Collection<Container> listContainers() throws Exception
    {
        if (account == null) {
            checkConnection();
        }

        return account.list();
    }

    /**
     * We want to be able to timeout quickly if we have a wrong URL or port
     * (instead of the default 2 minutes, because we are making the user wait while checking).
     * This builds the custom HttpClient needed to achieve this goal.
     *
     * @return the HttpClient with the custom timeout values
     */
    private HttpClient buildHttpClient()
    {
        HttpClientBuilder httpClient = HttpClientBuilder.create();

        RequestConfig config = RequestConfig.custom()
                .setConnectTimeout(CONNECTION_TIMEOUT.intValue())
                .setConnectionRequestTimeout(CONNECTION_TIMEOUT.intValue())
                .setSocketTimeout(CONNECTION_TIMEOUT.intValue()).build();

        httpClient.setDefaultRequestConfig(config);

        return httpClient.build();
    }
}
