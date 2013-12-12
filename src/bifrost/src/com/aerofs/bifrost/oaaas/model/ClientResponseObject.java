package com.aerofs.bifrost.oaaas.model;

/**
 * Represents a single Client object, as listed in the client list response (see bifrost_api.md)
 */
public class ClientResponseObject
{
    String clientId;
    String resourceServerKey;
    String clientName;
    String description;
    String contactEmail;
    String contactName;
    String redirectUri;
    String secret;

    public ClientResponseObject(String clientId, String resourceServerKey, String clientName,
            String description, String contactEmail, String contactName, String redirectUri,
            String secret)
    {
        this.clientId = clientId;
        this.resourceServerKey = resourceServerKey;
        this.clientName = clientName;
        this.description = description;
        this.contactEmail = contactEmail;
        this.contactName = contactName;
        this.redirectUri = redirectUri;
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

    public String getResourceServerKey()
    {
        return resourceServerKey;
    }

    public void setResourceServerKey(String resourceServerKey)
    {
        this.resourceServerKey = resourceServerKey;
    }

    public String getClientName()
    {
        return clientName;
    }

    public void setClientName(String clientName)
    {
        this.clientName = clientName;
    }

    public String getDescription()
    {
        return description;
    }

    public void setDescription(String description)
    {
        this.description = description;
    }

    public String getContactEmail()
    {
        return contactEmail;
    }

    public void setContactEmail(String contactEmail)
    {
        this.contactEmail = contactEmail;
    }

    public String getContactName()
    {
        return contactName;
    }

    public void setContactName(String contactName)
    {
        this.contactName = contactName;
    }

    public String getRedirectUri()
    {
        return redirectUri;
    }

    public void setRedirectUri(String redirectUri)
    {
        this.redirectUri = redirectUri;
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
