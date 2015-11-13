package com.aerofs.trifrost;

import com.aerofs.lib.log.LogUtil;
import com.aerofs.trifrost.api.OauthClient;
import com.aerofs.trifrost.api.OauthToken;
import com.aerofs.trifrost.api.VerifiedDevice;
import com.aerofs.trifrost.base.Constants;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.BaseEncoding;
import org.apache.http.client.fluent.Content;
import org.apache.http.client.fluent.Form;
import org.apache.http.client.fluent.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 */
public class SpartaClient implements ISpartaClient {
    private static final Logger logger = LoggerFactory.getLogger(Trifrost.class);
    private static ObjectMapper mapper = new ObjectMapper();
    private String deploymentSecret;
    private String oauthId;
    private String oauthSecret;

    @Inject
    public SpartaClient(@Named(Constants.DEPLOYMENT_SECRET_INJECTION_KEY) String deploymentSecret) throws IOException {
        this.deploymentSecret = deploymentSecret;

        try {
            Content content = Request.Get("http://sparta.service:8700/clients/aerofs-trifrost")
                    .addHeader("Accept", "application/json")
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Authorization", "Aero-Service-Shared-Secret aerofs-trifrost " + deploymentSecret)
                    .execute().returnContent();
            logger.info("Received client secret and id from bifrost.");

            OauthClient response = mapper.readValue(content.asStream(), OauthClient.class);
            oauthId = response.client_id;
            oauthSecret = response.secret;
            return;
        } catch (IOException e) {
            logger.info("Ignoring bifrost client lookup error - have to create the client", LogUtil.suppress(e));
        }

        // Didn't exist, let's create it:
        //
        try {
            Content content = Request.Post("http://sparta.service:8700/clients/")
                    .addHeader("Authorization", "Aero-Service-Shared-Secret aerofs-trifrost " + deploymentSecret)
                    .bodyForm(
                            Form.form()
                                    .add("client_id", "aerofs-trifrost")
                                    .add("client_name", "Trifrost")
                                    .add("redirect_uri", "aerofs://redirect")
                                    .add("resource_server_key", "oauth-havre")
                                    .add("expires", "0").build())
                    .execute().returnContent();
            logger.info("Received client secret and id by creating it on bifrost");

            OauthClient response = mapper.readValue(content.asStream(), OauthClient.class);
            oauthId = response.client_id;
            oauthSecret = response.secret;
            return;
        } catch (IOException e) {
            logger.warn("Critical error creating sparta client", e);
            throw e;
        }
    }

    @Override
    public VerifiedDevice getTokenForUser(String principal) throws IOException {
        // Let an exception here bubble up and bork the caller
        Content content = Request.Post("http://sparta.service:8700/delegate/token")
                .addHeader(
                        "Authorization",
                        "Aero-Delegated-User-Device aerofs-trifrost "
                                + this.deploymentSecret + " "
                                + BaseEncoding.base64().encode(principal.getBytes(StandardCharsets.UTF_8)))
                .bodyForm(Form.form() // form, form, form
                        .add("client_id", oauthId)
                        .add("client_secret", oauthSecret)
                        .add("grant_type", "delegated")
                        .build())
                .execute().returnContent();

        OauthToken oauthToken = mapper.readValue(content.asStream(), OauthToken.class);

        VerifiedDevice result = new VerifiedDevice();
        result.accessToken = oauthToken.access_token;
        result.accessTokenExpiration = oauthToken.expires_in;
        result.refreshToken = oauthToken.refresh_token;
        return result;
    }
}
