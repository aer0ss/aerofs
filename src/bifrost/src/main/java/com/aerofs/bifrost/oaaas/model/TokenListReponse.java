/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.bifrost.oaaas.model;

import java.util.List;

/**
 * Representation of the token list response as defined in bifrost_api.md
 */
public class TokenListReponse
{
    List<TokenResponseObject> tokens;

    public TokenListReponse(List<TokenResponseObject> tokens)
    {
        this.tokens = tokens;
    }

    public List<TokenResponseObject> getTokens()
    {
        return tokens;
    }

    public void setTokens(List<TokenResponseObject> tokens)
    {
        this.tokens = tokens;
    }
}
