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
    final @Nullable String device;

    public AuditCaller(UserID user)
    {
        email = user.getString();
        device = null;
    }

    public AuditCaller(UserID user, @Nonnull DID did)
    {
        email = user.getString();
        device = did.toStringFormal();
    }
}