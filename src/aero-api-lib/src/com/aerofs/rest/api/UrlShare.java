/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.rest.api;

import javax.annotation.Nullable;

public class UrlShare
{
    public final String key;
    public final String soid;
    public final String token;
    public final String createdBy;
    public final boolean requireLogin;
    public final boolean hasPassword;
    public final @Nullable Long expires;

    public UrlShare(String key, String soid, String token, String createdBy,
            boolean requireLogin, boolean hasPassword, @Nullable Long expires)
    {
        this.key = key;
        this.soid = soid;
        this.token = token;
        this.createdBy = createdBy;
        this.requireLogin = requireLogin;
        this.hasPassword = hasPassword;
        this.expires = expires;
    }
}
