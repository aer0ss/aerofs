/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.bifrost.oaaas.model;

import java.util.Date;

/**
 * Represents a single Token object, as listed in the token list response (see bifrost_api.md)
 */
public class TokenResponseObject
{
    String clientId;
    String clientName;
    Date creationDate;
    Long expires;
    String owner;
    String effectiveUser;
    String token;

    public TokenResponseObject(String clientId, String clientName, Date creationDate, Long expires,
            String owner, String effectiveUser, String token)
    {
        this.clientId = clientId;
        this.clientName = clientName;
        this.creationDate = creationDate;
        this.expires = expires;
        this.owner = owner;
        this.effectiveUser = effectiveUser;
        this.token = token;
    }

    public String getClientId()
    {
        return clientId;
    }

    public void setClientId(String clientId)
    {
        this.clientId = clientId;
    }

    public String getClientName()
    {
        return clientName;
    }

    public void setClientName(String clientName)
    {
        this.clientName = clientName;
    }

    public Date getCreationDate()
    {
        return creationDate;
    }

    public void setCreationDate(Date creationDate)
    {
        this.creationDate = creationDate;
    }

    public Long getExpires()
    {
        return expires;
    }

    public void setExpires(Long expires)
    {
        this.expires = expires;
    }

    public String getToken()
    {
        return token;
    }

    public void setToken(String token)
    {
        this.token = token;
    }
}

