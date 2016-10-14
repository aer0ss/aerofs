package com.aerofs.bifrost.oaaas.model;

/**
 * Representation of the client registration response as defined in bifrost_api.md
 */
public class NewClientResponse
{
    private String clientId;
    private String secret;

    public NewClientResponse(String clientId, String secret)
    {
        this.clientId = clientId;
        this.secret = secret;
    }

    public String getClientId()
    {
        return clientId;
    }

    public void setClientId(String clientId)
    {
        this.clientId = clientId;
    }

    public String getSecret()
    {
        return secret;
    }

    public void setSecret(String secret)
    {
        this.secret = secret;
    }
}
