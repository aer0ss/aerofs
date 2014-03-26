/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.server.audit;

import com.aerofs.base.NoObfuscation;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.UserID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

@NoObfuscation
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

    public AuditCaller(UserID user, @Nonnull UserID issuer, @Nonnull DID did)
    {
        email = issuer.getString();
        acting_as = issuer.equals(user) ? null : user.getString();
        device = did.toStringFormal();
    }
}