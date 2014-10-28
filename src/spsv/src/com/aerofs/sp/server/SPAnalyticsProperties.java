/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.server;

import com.aerofs.base.analytics.IAnalyticsPlatformProperties;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.UserID;
import com.aerofs.sp.server.lib.session.ISession;
import com.aerofs.sp.server.lib.session.ISession.ProvenanceGroup;

import javax.annotation.Nullable;

public class SPAnalyticsProperties implements IAnalyticsPlatformProperties
{
    private final ISession _session;

    SPAnalyticsProperties(ISession session)
    {
        _session = session;
    }

    @Override
    public @Nullable UserID getUserID()
    {
        try {
            return _session.getAuthenticatedUserWithProvenanceGroup(ProvenanceGroup.LEGACY).id();
        } catch (Throwable e) {
            // we don't want to crash SP if there's anything wrong with the analytics system.
            // This is why we catch Throwable and return a default value.
            return null;
        }
    }

    @Override
    public @Nullable DID getDid()
    {
        return null;
    }

    @Override
    public @Nullable String getVersion()
    {
        return null;
    }

    @Override
    public @Nullable String getOSFamily()
    {
        return null;
    }

    @Override
    public @Nullable String getOSName()
    {
        return null;
    }

    @Override
    public long getSignupDate()
    {
        try {
            return _session.getAuthenticatedUserWithProvenanceGroup(ProvenanceGroup.LEGACY)
                    .getSignupDate();
        } catch (Throwable e) {
            // See comment on why we catch Throwable above.
            return 0;
        }
    }

    @Nullable
    @Override
    public String getOrgID()
    {
        try {
            return _session.getAuthenticatedUserWithProvenanceGroup(ProvenanceGroup.LEGACY)
                    .getOrganization().id().toHexString();
        } catch (Throwable e) {
            return null;
        }
    }
}
