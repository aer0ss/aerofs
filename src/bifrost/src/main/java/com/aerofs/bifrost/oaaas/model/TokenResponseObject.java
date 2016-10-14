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

    public TokenResponseObject(AccessToken token)
    {
        this.clientId = token.getClientId();
        this.clientName = token.getClient().getName();
        this.creationDate = token.getCreationDate();
        this.expires = token.getExpires();
        this.owner = token.getOwner();
        this.effectiveUser = token.getEffectiveUserID();
        this.token = token.getToken();
    }
}

