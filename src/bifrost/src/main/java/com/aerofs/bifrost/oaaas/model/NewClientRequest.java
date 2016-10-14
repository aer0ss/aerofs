package com.aerofs.bifrost.oaaas.model;

import com.google.common.collect.Sets;

import javax.ws.rs.core.MultivaluedMap;
import java.util.Set;

/**
 * Representation of the client registration request as defined in bifrost_api.md
 */
public class NewClientRequest
{
    private String resourceServerKey;
    private String clientName;
    private String description;
    private String contactEmail;
    private String contactName;
    private String redirectUri;
    private String clientId;
    private String expires;
    private Set<String> scopes;

    public NewClientRequest() {
    }
    public static NewClientRequest fromMultiValuedFormParameters(MultivaluedMap<String, String> formParameters)
    {
        NewClientRequest ncr = new NewClientRequest();
        ncr.setResourceServerKey(formParameters.getFirst("resource_server_key"));
        ncr.setClientName(formParameters.getFirst("client_name"));
        ncr.setDescription(formParameters.getFirst("description"));
        ncr.setContactEmail(formParameters.getFirst("contact_email"));
        ncr.setContactName(formParameters.getFirst("contact_name"));
        ncr.setRedirectUri(formParameters.getFirst("redirect_uri"));
        ncr.setClientId(formParameters.getFirst("client_id"));
        ncr.setExpires(formParameters.getFirst("expires"));

        if (formParameters.containsKey("scopes")) {
            ncr.setScopes(Sets.newHashSet(formParameters.get("scopes")));
        } else {
            ncr.setScopes(Sets.newHashSet("files.read", "files.write"));
        }

        return ncr;
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

    public String getClientId()
    {
        return clientId;
    }

    public void setClientId(String clientId)
    {
        this.clientId = clientId;
    }

    public String getExpires()
    {
        return expires;
    }

    public void setExpires(String expires)
    {
        this.expires = expires;
    }

    public void setScopes(Set<String> scopes)
    {
        this.scopes = scopes;
    }

    public Set<String> getScopes()
    {
        return this.scopes;
    }
}
