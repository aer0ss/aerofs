package com.aerofs.trifrost.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class OauthToken {
    public String access_token;
    public String refresh_token;
    public String token_type;
    public long expires_in;
    // this is annoying but it's how Oauth returns things.
    public String scope;
}
