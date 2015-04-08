package com.aerofs.gui.multiuser.setup;

import com.aerofs.base.C;
import com.aerofs.controller.SetupModel;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.HttpClientBuilder;
import org.javaswift.joss.client.factory.AccountConfig;
import org.javaswift.joss.client.factory.AccountFactory;


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

    // Timeout when checking the connection
    public static final Long CONNECTION_TIMEOUT = 10* C.SEC;

    public SwiftConnectionChecker(SetupModel model)
    {
        _model = model;
    }

    public void checkConnection() throws Exception
    {
        AccountConfig config = new AccountConfig();
        config.setUsername(_model._backendConfig._swiftConfig._username);
        config.setPassword(_model._backendConfig._swiftConfig._password);
        config.setAuthUrl(_model._backendConfig._swiftConfig._url);
        config.setAuthenticationMethod(_model._backendConfig._swiftConfig._authMode);
        new AccountFactory(config)
                .setHttpClient(buildHttpClient())
                .createAccount();
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
