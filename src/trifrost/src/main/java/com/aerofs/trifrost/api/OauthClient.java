package com.aerofs.trifrost.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * ... Just a small set of the actual response that we care about; client id and secret.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class OauthClient {
    public String client_id;
    public String secret;
}
