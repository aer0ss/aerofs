/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.server.audit;

import com.aerofs.ids.UniqueID;
import com.aerofs.ids.UserID;
import com.aerofs.rest.auth.IUserAuthToken;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class AuditCaller
{
    final String email;
    final String acting_as;
    final @Nullable String device;

    public AuditCaller(UserID user)
    {
        email = user.getString();
        acting_as = null;
        device = null;
    }

    public AuditCaller(UserID user, @Nonnull UserID issuer, @Nonnull UniqueID id)
    {
        email = issuer.getString();
        acting_as = issuer.equals(user) ? null : user.getString();
        device = id.toStringFormal();
    }

    public static AuditCaller fromUserAuthToken(IUserAuthToken token)
    {
        return new AuditCaller(token.user(), token.issuer(), token.uniqueId());
    }
}
